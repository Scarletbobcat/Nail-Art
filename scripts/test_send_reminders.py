from datetime import datetime

import pytest

import send_reminders
from send_reminders import (
    build_message,
    creds_complete,
    deliver,
    fetch_appointments,
    preview,
    row_twilio_creds,
)


WHEN = datetime(2026, 4, 10, 10, 0)


def make_row(**overrides) -> dict:
    row = {
        "id": "appt-1",
        "customer_name": "Jane Doe",
        "phone_number": "330-555-1000",
        "starts_at_local": WHEN,
        "timezone": "America/New_York",
        "organization_name": "Salon Alpha",
        "organization_phone": "330-111-1111",
        "twilio_account_sid": "ACalpha",
        "twilio_phone_number": "+15550001111",
        "twilio_auth_token": "ALPHA-TOKEN",
    }
    row.update(overrides)
    return row


class FakeCursor:
    def __init__(self, rows=None, raise_on_execute=None):
        self.rows = rows or []
        self.raise_on_execute = raise_on_execute
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql, params=None):
        self.executed.append((sql, params))
        if self.raise_on_execute is not None:
            raise self.raise_on_execute

    def fetchall(self):
        return self.rows


class FakeConn:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self, *args, **kwargs):
        return self._cursor

    def close(self):
        pass


class RecordingSender:
    def __init__(self, result=(True, "SMxxxx")):
        self.result = result
        self.calls = []

    def __call__(self, creds, to, body):
        self.calls.append((creds, to, body))
        return self.result


# ---- build_message (unchanged behavior) ----

def test_build_message_uses_per_org_salon_identity() -> None:
    message_a = build_message(WHEN, "Salon Alpha", "330-111-1111")
    message_b = build_message(WHEN, "Salon Bravo", "330-222-2222")

    assert "Salon Alpha" in message_a and "330-111-1111" in message_a
    assert "Salon Bravo" not in message_a and "330-222-2222" not in message_a
    assert "Salon Bravo" in message_b and "330-222-2222" in message_b
    assert "Salon Alpha" not in message_b and "330-111-1111" not in message_b


def test_build_message_has_no_hardcoded_salon() -> None:
    message = build_message(WHEN, "Salon Alpha", "330-111-1111")

    assert "Nail Art & Spa LLC." not in message
    assert "330-758-6633" not in message


def test_build_message_tolerates_missing_business_phone() -> None:
    message = build_message(WHEN, "Salon Alpha", None)

    assert "Salon Alpha" in message
    assert "Please call the salon if you need to reschedule or cancel." in message


# ---- per-org credential helpers ----

def test_row_twilio_creds_maps_per_org_fields() -> None:
    creds = row_twilio_creds(make_row())
    assert creds == ("ACalpha", "ALPHA-TOKEN", "+15550001111")


def test_creds_complete_requires_all_three_nonblank() -> None:
    assert creds_complete(("AC", "tok", "+1555")) is True
    assert creds_complete((None, "tok", "+1555")) is False
    assert creds_complete(("AC", None, "+1555")) is False
    assert creds_complete(("AC", "tok", None)) is False
    assert creds_complete(("AC", "tok", "   ")) is False


# ---- deliver loop ----

def test_deliver_completeCreds_sendsFromThatRowsOwnNumber() -> None:
    sender = RecordingSender()
    marked = []

    sent, failed, skipped = deliver([make_row()], True, sender, marked.append)

    assert (sent, failed, skipped) == (1, 0, 0)
    assert len(sender.calls) == 1
    creds, to, _body = sender.calls[0]
    assert creds == ("ACalpha", "ALPHA-TOKEN", "+15550001111")
    assert to == "330-555-1000"
    assert marked == ["appt-1"]


def test_deliver_incompleteCreds_skipsWithoutSending() -> None:
    sender = RecordingSender()
    marked = []
    row = make_row(twilio_auth_token=None)  # missing token

    sent, failed, skipped = deliver([row], True, sender, marked.append)

    assert (sent, failed, skipped) == (0, 0, 1)
    assert sender.calls == []
    assert marked == []


def test_deliver_dryRun_neverSends() -> None:
    sender = RecordingSender()

    sent, failed, skipped = deliver([make_row()], False, sender, lambda _id: None)

    assert (sent, failed, skipped) == (0, 0, 0)
    assert sender.calls == []


def test_deliver_neverPrintsDecryptedToken(capsys) -> None:
    sender = RecordingSender()
    row = make_row(twilio_auth_token="SUPER-SECRET-TOKEN-XYZ")

    deliver([row], True, sender, lambda _id: None)
    # also exercise the skip path's stderr line
    deliver([make_row(twilio_auth_token=None, organization_name="Salon Beta")], True, sender, lambda _id: None)

    captured = capsys.readouterr()
    assert "SUPER-SECRET-TOKEN-XYZ" not in captured.out
    assert "SUPER-SECRET-TOKEN-XYZ" not in captured.err


# ---- fetch query construction ----

def test_fetch_appointments_withKey_decryptsTokenAndBindsKeyParam() -> None:
    cursor = FakeCursor(rows=[make_row()])
    conn = FakeConn(cursor)

    rows = fetch_appointments(conn, None, "the-key")

    assert rows == [make_row()]
    sql, params = cursor.executed[0]
    assert "pgp_sym_decrypt(s.twilio_auth_token, %(key)s)" in sql
    assert params == {"key": "the-key"}


def test_fetch_appointments_dryRunNoKey_doesNotDecrypt() -> None:
    cursor = FakeCursor(rows=[])
    conn = FakeConn(cursor)

    fetch_appointments(conn, None, None)

    sql, params = cursor.executed[0]
    assert "pgp_sym_decrypt" not in sql
    assert "null as twilio_auth_token" in sql
    assert params is None


def test_fetch_appointments_explicitDateWithKey_bindsBothParams() -> None:
    cursor = FakeCursor(rows=[])
    conn = FakeConn(cursor)

    fetch_appointments(conn, "2026-04-10", "the-key")

    sql, params = cursor.executed[0]
    assert "%(date)s::date" in sql
    assert params == {"key": "the-key", "date": "2026-04-10"}


def test_fetch_appointments_wrongKey_raisesRatherThanReturningEmpty() -> None:
    cursor = FakeCursor(raise_on_execute=RuntimeError("Wrong key or corrupt data"))
    conn = FakeConn(cursor)

    with pytest.raises(RuntimeError, match="Wrong key or corrupt data"):
        fetch_appointments(conn, None, "wrong-key")


def test_main_decryptFailure_exitsNonZero(monkeypatch, capsys) -> None:
    cursor = FakeCursor(raise_on_execute=RuntimeError("Wrong key or corrupt data"))
    monkeypatch.setattr(send_reminders.psycopg, "connect", lambda *a, **k: FakeConn(cursor))
    monkeypatch.setenv("APP_ENCRYPTION_KEY", "some-key")
    monkeypatch.setattr(
        "sys.argv",
        ["send_reminders.py", "--send", "--db-url", "postgresql://localhost/test"],
    )

    code = send_reminders.main()

    assert code == 1
    assert "wrong APP_ENCRYPTION_KEY" in capsys.readouterr().err
