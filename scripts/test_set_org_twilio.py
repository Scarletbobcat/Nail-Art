import pytest

import set_org_twilio as cutover
from set_org_twilio import (
    SetOrgTwilioError,
    parse_args,
    read_auth_token,
    set_org_twilio,
)


class FakeCursor:
    def __init__(self, org_row=("ORG-UUID",)):
        self.org_row = org_row
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql, params=None):
        self.executed.append((sql, params))

    def fetchone(self):
        return self.org_row


class FakeTransaction:
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeConn:
    def __init__(self, cursor):
        self._cursor = cursor

    def transaction(self):
        return FakeTransaction()

    def cursor(self, *args, **kwargs):
        return self._cursor

    def close(self):
        pass


def test_read_auth_token_prefersEnv(monkeypatch) -> None:
    monkeypatch.setenv("TWILIO_AUTH_TOKEN", "tok-from-env")
    assert read_auth_token() == "tok-from-env"


def test_read_auth_token_absentNonInteractive_raises(monkeypatch) -> None:
    monkeypatch.delenv("TWILIO_AUTH_TOKEN", raising=False)
    monkeypatch.setattr("sys.stdin.isatty", lambda: False)
    with pytest.raises(SetOrgTwilioError):
        read_auth_token()


def test_authToken_isNotACliArgument(monkeypatch) -> None:
    # The secret must never be passable on argv (it would hit shell history).
    monkeypatch.setattr(
        "sys.argv",
        ["set_org_twilio.py", "--org-id", "abc", "--auth-token", "secret"],
    )
    with pytest.raises(SystemExit):
        parse_args()


def test_set_org_twilio_usesPinnedEncryptSignature(monkeypatch) -> None:
    cursor = FakeCursor(org_row=("ORG-UUID",))
    monkeypatch.setattr(cutover.psycopg, "connect", lambda *a, **k: FakeConn(cursor))

    result = set_org_twilio(
        "postgresql://localhost/test",
        org_id="ORG-UUID",
        org_name=None,
        account_sid="ACtest",
        phone_number="+15551230000",
        auth_token="the-token",
        key="the-key",
    )

    assert result == "ORG-UUID"
    upsert_sql, params = cursor.executed[-1]
    assert "pgp_sym_encrypt(%(token)s, %(key)s)" in upsert_sql
    assert "on conflict (organization_id)" in upsert_sql
    assert params["token"] == "the-token"
    assert params["key"] == "the-key"


def test_set_org_twilio_missingSidOrPhone_raises(monkeypatch) -> None:
    monkeypatch.setattr(
        cutover.psycopg, "connect", lambda *a, **k: FakeConn(FakeCursor())
    )
    with pytest.raises(SetOrgTwilioError):
        set_org_twilio(
            "postgresql://localhost/test",
            org_id="ORG-UUID",
            org_name=None,
            account_sid=None,
            phone_number="+15551230000",
            auth_token="the-token",
            key="the-key",
        )


def test_main_missingEncryptionKey_exitsNonZero(monkeypatch, capsys) -> None:
    monkeypatch.delenv("APP_ENCRYPTION_KEY", raising=False)
    monkeypatch.setattr(
        "sys.argv",
        ["set_org_twilio.py", "--db-url", "postgresql://localhost/test", "--org-id", "abc"],
    )
    # No DB connection should be attempted before the key check fails.
    monkeypatch.setattr(
        cutover.psycopg,
        "connect",
        lambda *a, **k: (_ for _ in ()).throw(AssertionError("must not connect")),
    )

    code = cutover.main()

    assert code == 1
    assert "APP_ENCRYPTION_KEY is required" in capsys.readouterr().err
