import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
from base64 import b64encode
from datetime import date, datetime, timezone

import psycopg
from dotenv import load_dotenv
from psycopg.rows import dict_row

load_dotenv()


TWILIO_API_URL = "https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json"


class SendRemindersError(Exception):
    pass


def postgres_url(cli_url: str | None) -> str:
    url = cli_url or os.getenv("POSTGRES_URL")
    if not url:
        raise SendRemindersError("POSTGRES_URL is required or pass --db-url")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def encryption_key() -> str:
    """The pgcrypto symmetric key, used to decrypt each org's Twilio auth token
    inside Postgres. Same value as the Java app's APP_ENCRYPTION_KEY. Required
    only when actually sending (the dry run never decrypts)."""
    key = os.getenv("APP_ENCRYPTION_KEY")
    if not key:
        raise SendRemindersError("APP_ENCRYPTION_KEY is required (to decrypt per-org Twilio tokens)")
    return key


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Manually send the daily SMS reminders. Defaults to dry run.",
    )
    parser.add_argument("--db-url", default=None, help="Postgres URL; defaults to POSTGRES_URL")
    parser.add_argument(
        "--date",
        default=None,
        help="YYYY-MM-DD date (in each org's timezone) to send reminders for. Defaults to tomorrow.",
    )
    parser.add_argument(
        "--send",
        action="store_true",
        help="Actually call Twilio and mark reminder_sent_at. Without this flag the script only prints.",
    )
    return parser.parse_args()


def fetch_appointments(
    conn: psycopg.Connection, target_date: str | None, key: str | None
) -> list[dict]:
    # The auth token is decrypted in Postgres (same pinned pgp_sym_decrypt(token, key)
    # contract as the Java app). A wrong/mismatched key makes this RAISE here, which
    # propagates loudly rather than silently returning nothing. In dry-run mode no key
    # is supplied and the token is not decrypted at all.
    token_expr = "pgp_sym_decrypt(s.twilio_auth_token, %(key)s)" if key else "null"
    base_sql = f"""
        select
            a.id,
            a.customer_name,
            a.phone_number,
            (a.starts_at at time zone o.timezone) as starts_at_local,
            o.timezone,
            o.name as organization_name,
            o.business_phone as organization_phone,
            s.twilio_account_sid,
            s.twilio_phone_number,
            {token_expr} as twilio_auth_token
        from appointments a
        join organizations o on o.id = a.organization_id
        join organization_settings s on s.organization_id = a.organization_id
        where s.sms_reminders_enabled = true
          and a.phone_number is not null
          and a.phone_number <> ''
          and a.reminder_sent_at is null
          and a.archived_at is null
          and (a.starts_at at time zone o.timezone)::date = {{date_expr}}
        order by o.name, a.starts_at
    """
    params: dict = {}
    if key:
        params["key"] = key
    if target_date is None:
        sql = base_sql.format(date_expr="((now() at time zone o.timezone)::date + interval '1 day')")
    else:
        sql = base_sql.format(date_expr="%(date)s::date")
        params["date"] = target_date
    with conn.cursor(row_factory=dict_row) as cur:
        cur.execute(sql, params or None)
        return list(cur.fetchall())


def row_twilio_creds(row: dict) -> tuple[str | None, str | None, str | None]:
    """Per-org (sid, token, from_number) for one appointment row."""
    return (
        row.get("twilio_account_sid"),
        row.get("twilio_auth_token"),
        row.get("twilio_phone_number"),
    )


def creds_complete(creds: tuple[str | None, str | None, str | None]) -> bool:
    return all(value is not None and str(value).strip() != "" for value in creds)


def preview(row: dict) -> str:
    # Intentionally excludes the decrypted auth token — it must never be printed.
    return (
        f"  [{row['organization_name']}] {row['customer_name']} "
        f"{row['phone_number']} @ {row['starts_at_local']:%a %b %d %I:%M %p}"
    )


def build_message(starts_at_local: datetime, salon_name: str, salon_phone: str | None) -> str:
    day = starts_at_local.strftime("%A")
    when = starts_at_local.strftime("%b %-d at %-I:%M %p")
    call_to_action = (
        f"Please call the salon at {salon_phone} if you "
        if salon_phone
        else "Please call the salon if you "
    )
    return (
        f"Hello! This is {salon_name}, and this is a reminder for your appointment on "
        f"{day}, {when}. {call_to_action}"
        f"need to reschedule or cancel. We look forward to seeing you!\n\n"
        f"Reply STOP to stop receiving messages from this number."
    )


def send_sms(creds: tuple[str, str, str], to: str, body: str) -> tuple[bool, str]:
    sid, token, from_number = creds
    auth = b64encode(f"{sid}:{token}".encode()).decode()
    data = urllib.parse.urlencode({"To": to, "From": from_number, "Body": body}).encode()
    req = urllib.request.Request(
        TWILIO_API_URL.format(sid=sid),
        data=data,
        headers={
            "Authorization": f"Basic {auth}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            payload = json.loads(resp.read().decode())
            return True, payload.get("sid", "")
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        return False, f"HTTP {e.code}: {body}"
    except Exception as e:
        return False, f"{type(e).__name__}: {e}"


def mark_sent(conn: psycopg.Connection, appointment_id) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "update appointments set reminder_sent_at = %s where id = %s",
            (datetime.now(timezone.utc), appointment_id),
        )


def deliver(appts: list[dict], send: bool, send_fn, mark_fn) -> tuple[int, int, int]:
    """Returns (sent, failed, skipped). Each org sends from its OWN credentials;
    a row with incomplete Twilio config is skipped, never sent from another org's
    number. The decrypted token is never printed — only `preview()` is logged."""
    sent = failed = skipped = 0
    for a in appts:
        line = preview(a)
        if not send:
            print(line + "  (dry run)")
            continue
        creds = row_twilio_creds(a)
        if not creds_complete(creds):
            skipped += 1
            print(line + "  SKIPPED: incomplete Twilio config", file=sys.stderr)
            continue
        ok, info = send_fn(
            creds,
            a["phone_number"],
            build_message(a["starts_at_local"], a["organization_name"], a["organization_phone"]),
        )
        if ok:
            mark_fn(a["id"])
            sent += 1
            print(line + f"  sent ({info})")
        else:
            failed += 1
            print(line + f"  FAILED: {info}", file=sys.stderr)
    return sent, failed, skipped


def main() -> int:
    args = parse_args()
    try:
        db_url = postgres_url(args.db_url)
        key = encryption_key() if args.send else None
    except SendRemindersError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    conn = psycopg.connect(db_url, autocommit=True)
    try:
        try:
            appts = fetch_appointments(conn, args.date, key)
        except Exception as exc:
            # A decrypt failure (wrong APP_ENCRYPTION_KEY) lands here: fail loudly,
            # non-zero — never a silent "0 sendable reminders" success.
            print(f"failed to load reminders (wrong APP_ENCRYPTION_KEY?): {exc}", file=sys.stderr)
            return 1

        label = args.date or "tomorrow (in each org's tz)"
        print(f"{len(appts)} sendable reminder(s) for {label}")
        if not appts:
            return 0

        sent, failed, skipped = deliver(appts, args.send, send_sms, lambda appt_id: mark_sent(conn, appt_id))

        if args.send:
            print(f"\nDone. sent={sent} failed={failed} skipped={skipped}")
        else:
            print("\nDry run. Re-run with --send to actually send.")
        return 0 if failed == 0 else 2
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
