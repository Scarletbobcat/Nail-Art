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
SALON_NAME = "Nail Art & Spa LLC."
SALON_PHONE = "330-758-6633"


class SendRemindersError(Exception):
    pass


def postgres_url(cli_url: str | None) -> str:
    url = cli_url or os.getenv("POSTGRES_URL")
    if not url:
        raise SendRemindersError("POSTGRES_URL is required or pass --db-url")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def twilio_creds() -> tuple[str, str, str]:
    sid = os.getenv("TWILIO_ACCOUNT_SID")
    token = os.getenv("TWILIO_AUTH_TOKEN")
    from_number = os.getenv("TWILIO_PHONE_NUMBER")
    missing = [name for name, val in (
        ("TWILIO_ACCOUNT_SID", sid),
        ("TWILIO_AUTH_TOKEN", token),
        ("TWILIO_PHONE_NUMBER", from_number),
    ) if not val]
    if missing:
        raise SendRemindersError(f"missing env vars: {', '.join(missing)}")
    return sid, token, from_number


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


def fetch_appointments(conn: psycopg.Connection, target_date: str | None) -> list[dict]:
    base_sql = """
        select
            a.id,
            a.customer_name,
            a.phone_number,
            (a.starts_at at time zone o.timezone) as starts_at_local,
            o.timezone,
            o.name as organization_name
        from appointments a
        join organizations o on o.id = a.organization_id
        join organization_settings s on s.organization_id = a.organization_id
        where s.sms_reminders_enabled = true
          and a.phone_number is not null
          and a.phone_number <> ''
          and a.reminder_sent_at is null
          and a.archived_at is null
          and (a.starts_at at time zone o.timezone)::date = {date_expr}
        order by o.name, a.starts_at
    """
    with conn.cursor(row_factory=dict_row) as cur:
        if target_date is None:
            cur.execute(base_sql.format(
                date_expr="((now() at time zone o.timezone)::date + interval '1 day')"
            ))
        else:
            cur.execute(
                base_sql.format(date_expr="%s::date"),
                (target_date,),
            )
        return list(cur.fetchall())


def build_message(starts_at_local: datetime) -> str:
    day = starts_at_local.strftime("%A")
    when = starts_at_local.strftime("%b %-d at %-I:%M %p")
    return (
        f"Hello! This is {SALON_NAME}, and this is a reminder for your appointment on "
        f"{day}, {when}. Please call the salon at {SALON_PHONE} if you "
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


def main() -> int:
    args = parse_args()
    try:
        db_url = postgres_url(args.db_url)
        creds = twilio_creds() if args.send else None
    except SendRemindersError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    conn = psycopg.connect(db_url, autocommit=True)
    try:
        appts = fetch_appointments(conn, args.date)
        label = args.date or "tomorrow (in each org's tz)"
        print(f"{len(appts)} sendable reminder(s) for {label}")
        if not appts:
            return 0

        sent = failed = 0
        for a in appts:
            preview = (
                f"  [{a['organization_name']}] {a['customer_name']} "
                f"{a['phone_number']} @ {a['starts_at_local']:%a %b %d %I:%M %p}"
            )
            if not args.send:
                print(preview + "  (dry run)")
                continue
            ok, info = send_sms(creds, a["phone_number"], build_message(a["starts_at_local"]))
            if ok:
                mark_sent(conn, a["id"])
                sent += 1
                print(preview + f"  sent ({info})")
            else:
                failed += 1
                print(preview + f"  FAILED: {info}", file=sys.stderr)

        if args.send:
            print(f"\nDone. sent={sent} failed={failed}")
        else:
            print("\nDry run. Re-run with --send to actually send.")
        return 0 if failed == 0 else 2
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
