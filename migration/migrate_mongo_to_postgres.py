"""
One-shot MongoDB -> PostgreSQL migration.

Wipes the target Postgres in a single transaction and reinserts every row from
Mongo. Safe to rerun: same Mongo input always produces the same Postgres state
(modulo freshly minted UUIDs). RefreshToken and Counters collections are
skipped on purpose -- ephemeral auth state and sequence counters do not carry
forward.

Connection strings come from .env (MONGO_URI, POSTGRES_URL, optionally
MONGO_DB). Per-run inputs (org name, owner credentials) are CLI args so each
run is explicit. See --help.
"""

import argparse
import getpass
import os
import sys
import uuid
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

import bcrypt
import psycopg
import pymongo
from dotenv import load_dotenv

load_dotenv()


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        print(f"ERROR: required env var {name} is not set", file=sys.stderr)
        sys.exit(1)
    return value


def parse_local_dt(date_str: str, time_str: str, tz: ZoneInfo) -> datetime:
    # date_str like "2026-05-25", time_str like "T09:30" or "T9:30"
    t = time_str[1:] if time_str.startswith("T") else time_str
    h, m = t.split(":")
    d = date.fromisoformat(date_str)
    return datetime.combine(d, time(int(h), int(m)), tzinfo=tz)


def insert_appointment(cur, mongo_appt, org_id, emp_map, svc_map, client_map, tz, archived_at):
    appt_id = uuid.uuid4()
    starts_at = parse_local_dt(mongo_appt["date"], mongo_appt["startTime"], tz)
    ends_at = parse_local_dt(mongo_appt["date"], mongo_appt["endTime"], tz)

    raw_client_id = mongo_appt.get("clientId")
    client_id = client_map.get(raw_client_id) if raw_client_id is not None else None
    employee_id = emp_map[mongo_appt["employeeId"]]

    reminder_sent_at = starts_at - timedelta(days=1) if mongo_appt.get("reminderSent") else None
    phone = mongo_appt.get("phoneNumber") or None

    cur.execute(
        """INSERT INTO appointments
           (id, organization_id, client_id, employee_id, starts_at, ends_at,
            customer_name, phone_number, reminder_sent_at, showed_up, archived_at)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (appt_id, org_id, client_id, employee_id, starts_at, ends_at,
         mongo_appt["name"], phone, reminder_sent_at,
         bool(mongo_appt.get("showedUp", False)), archived_at),
    )

    for sid in (mongo_appt.get("services") or []):
        cur.execute(
            """INSERT INTO appointment_services (organization_id, appointment_id, service_id)
               VALUES (%s, %s, %s)""",
            (org_id, appt_id, svc_map[sid]),
        )


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--org-name", required=True,
                        help="Name of the organization to create")
    parser.add_argument("--org-phone", default=None,
                        help="Optional business phone for the organization")
    parser.add_argument("--org-timezone", default="America/New_York",
                        help="IANA timezone for the organization (default: America/New_York)")
    parser.add_argument("--owner-username", required=True,
                        help="Username of the single owner user to create")
    parser.add_argument("--owner-email", default=None,
                        help="Optional email for the owner")
    parser.add_argument("--owner-password", default=None,
                        help="Owner password (plaintext, bcrypted at write time). "
                             "If omitted, prompted interactively to keep it out of shell history.")
    parser.add_argument("--dry-run", action="store_true",
                        help="run inside a transaction then rollback")
    args = parser.parse_args()

    mongo_uri = require_env("MONGO_URI")
    postgres_url = require_env("POSTGRES_URL")
    mongo_db_name = os.getenv("MONGO_DB", "Nail-Art")

    org_name = args.org_name
    org_phone = args.org_phone or None
    org_tz_name = args.org_timezone
    owner_username = args.owner_username
    owner_email = args.owner_email or None
    owner_password = args.owner_password or getpass.getpass("Owner password: ")
    if not owner_password:
        print("ERROR: owner password is required", file=sys.stderr)
        sys.exit(1)

    org_tz = ZoneInfo(org_tz_name)

    mongo = pymongo.MongoClient(mongo_uri)[mongo_db_name]

    print(f"Reading from MongoDB:  {mongo_db_name}")
    print(f"Writing to Postgres:   {postgres_url.rsplit('@', 1)[-1] if '@' in postgres_url else postgres_url}")
    print(f"Organization:          {org_name!r} (tz={org_tz_name})")
    print(f"Owner user:            {owner_username!r}")
    print(f"Mode:                  {'DRY RUN (will rollback)' if args.dry_run else 'COMMIT'}")
    print()

    employees = list(mongo["Employees"].find({}))
    services = list(mongo["Services"].find({}))
    clients = list(mongo["Clients"].find({}))
    live_appts = list(mongo["Appointments"].find({}))
    archived_appts = list(mongo["ArchivedAppointments"].find({}))

    print("Source row counts:")
    print(f"  employees:             {len(employees)}")
    print(f"  services:              {len(services)}")
    print(f"  clients:               {len(clients)}")
    print(f"  live appointments:     {len(live_appts)}")
    print(f"  archived appointments: {len(archived_appts)}")
    print()

    # bcrypt strength 10 matches Spring's BCryptPasswordEncoder default.
    # Python bcrypt outputs $2b$ which jBCrypt accepts alongside $2a$.
    owner_password_hash = bcrypt.hashpw(
        owner_password.encode("utf-8"), bcrypt.gensalt(rounds=10)
    ).decode("utf-8")

    org_id = uuid.uuid4()
    owner_user_id = uuid.uuid4()
    emp_map = {e["id"]: uuid.uuid4() for e in employees}
    svc_map = {s["id"]: uuid.uuid4() for s in services}
    client_map = {c["id"]: uuid.uuid4() for c in clients}

    conn = psycopg.connect(postgres_url)
    try:
        with conn.cursor() as cur:
            cur.execute("TRUNCATE TABLE organizations, users RESTART IDENTITY CASCADE")

            cur.execute(
                """INSERT INTO organizations (id, name, business_phone, timezone)
                   VALUES (%s, %s, %s, %s)""",
                (org_id, org_name, org_phone, org_tz_name),
            )
            cur.execute(
                """INSERT INTO organization_settings (organization_id, sms_reminders_enabled)
                   VALUES (%s, %s)""",
                (org_id, False),
            )
            cur.execute(
                """INSERT INTO users (id, username, email, password_hash)
                   VALUES (%s, %s, %s, %s)""",
                (owner_user_id, owner_username, owner_email, owner_password_hash),
            )
            cur.execute(
                """INSERT INTO organization_users (organization_id, user_id, role)
                   VALUES (%s, %s, %s)""",
                (org_id, owner_user_id, "owner"),
            )

            for e in employees:
                cur.execute(
                    """INSERT INTO employees (id, organization_id, name, color, active)
                       VALUES (%s, %s, %s, %s, %s)""",
                    (emp_map[e["id"]], org_id, e["name"], e.get("color"), True),
                )

            for s in services:
                cur.execute(
                    """INSERT INTO services (id, organization_id, name, active)
                       VALUES (%s, %s, %s, %s)""",
                    (svc_map[s["id"]], org_id, s["name"], True),
                )

            for c in clients:
                phone = c.get("phoneNumber") or None
                cur.execute(
                    """INSERT INTO clients (id, organization_id, name, phone_number)
                       VALUES (%s, %s, %s, %s)""",
                    (client_map[c["id"]], org_id, c["name"], phone),
                )

            archive_ts = datetime.now(org_tz)

            for a in live_appts:
                insert_appointment(cur, a, org_id, emp_map, svc_map, client_map, org_tz, None)

            for a in archived_appts:
                insert_appointment(cur, a, org_id, emp_map, svc_map, client_map, org_tz, archive_ts)

            def count(table):
                cur.execute(f"SELECT count(*) FROM {table}")
                return cur.fetchone()[0]

            print("Postgres row counts after migration:")
            print(f"  organizations:         {count('organizations')}")
            print(f"  organization_users:    {count('organization_users')}")
            print(f"  organization_settings: {count('organization_settings')}")
            print(f"  users:                 {count('users')}")
            print(f"  employees:             {count('employees')}")
            print(f"  services:              {count('services')}")
            print(f"  clients:               {count('clients')}")
            print(f"  appointments:          {count('appointments')}")
            print(f"  appointment_services:  {count('appointment_services')}")
            print()

        if args.dry_run:
            conn.rollback()
            print("DRY RUN — transaction rolled back. No changes committed.")
        else:
            conn.commit()
            print("Migration committed.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
