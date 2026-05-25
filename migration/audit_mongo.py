"""
Read-only audit of the MongoDB data against the V1 Postgres schema constraints.

Reports every row that would block the upcoming Mongo -> Postgres migration:
NOT NULL violations, unique-constraint violations, CHECK violations,
unparseable dates/times, and orphan foreign-key references.

Run with MONGO_URI pointed at the source database. Makes no writes.
"""

import os
import re
from datetime import datetime
from dotenv import load_dotenv
import pymongo

load_dotenv()

connection_string = os.getenv("MONGO_URI")
db_name = os.getenv("MONGO_DB", "Nail-Art")

mongo_client = pymongo.MongoClient(connection_string)
db = mongo_client[db_name]

clients = db["Clients"]
employees = db["Employees"]
services_col = db["Services"]
appointments = db["Appointments"]
archived_appointments = db["ArchivedAppointments"]
users = db["Users"]
refresh_tokens = db["RefreshToken"]

issue_categories = 0


def section(title):
    print(f"\n=== {title} ===")


def report(label, count, samples=None):
    global issue_categories
    marker = "  " if count == 0 else "! "
    print(f"{marker}{label}: {count}")
    if count > 0:
        issue_categories += 1
    if samples:
        for s in samples[:5]:
            print(f"    - {s}")
        if len(samples) > 5:
            print(f"    ... and {len(samples) - 5} more")


# --- Row counts ----------------------------------------------------------
section("Row counts")
print(f"  users:                {users.count_documents({})}")
print(f"  clients:              {clients.count_documents({})}")
print(f"  employees:            {employees.count_documents({})}")
print(f"  services:             {services_col.count_documents({})}")
print(f"  appointments:         {appointments.count_documents({})}")
print(f"  archived appointments:{archived_appointments.count_documents({})}")
print(f"  refresh tokens:       {refresh_tokens.count_documents({})}")


# --- Users: unique + NOT NULL constraints --------------------------------
section("Users — V1 requires username NOT NULL UNIQUE (citext) and password_hash NOT NULL")

empty_username = list(users.find(
    {"$or": [{"username": None}, {"username": ""}]},
    {"id": 1, "username": 1}
))
report("users with empty/null username", len(empty_username),
       [f"id={u.get('id')}" for u in empty_username])

empty_password = list(users.find(
    {"$or": [{"password": None}, {"password": ""}]},
    {"id": 1, "username": 1}
))
report("users with empty/null password", len(empty_password),
       [f"id={u.get('id')}, username={u.get('username')!r}" for u in empty_password])

dup_usernames = list(users.aggregate([
    {"$match": {"username": {"$ne": None, "$ne": ""}}},
    {"$group": {"_id": {"$toLower": "$username"}, "count": {"$sum": 1}, "ids": {"$push": "$id"}}},
    {"$match": {"count": {"$gt": 1}}},
]))
report("duplicate usernames (case-insensitive)", len(dup_usernames),
       [f"{d['_id']!r}: ids={d['ids']}" for d in dup_usernames])


# --- Clients: unique phone + NOT NULL name -------------------------------
section("Clients — V1 requires name NOT NULL and unique (organization_id, phone_number) where phone_number <> ''")

empty_client_name = list(clients.find(
    {"$or": [{"name": None}, {"name": ""}]},
    {"id": 1, "phoneNumber": 1}
))
report("clients with empty/null name", len(empty_client_name),
       [f"id={c.get('id')}, phone={c.get('phoneNumber')}" for c in empty_client_name])

dup_phones = list(clients.aggregate([
    {"$match": {"phoneNumber": {"$ne": None, "$ne": ""}}},
    {"$group": {"_id": "$phoneNumber", "count": {"$sum": 1}, "ids": {"$push": "$id"}}},
    {"$match": {"count": {"$gt": 1}}},
    {"$sort": {"count": -1}},
]))
report("duplicate phone-number groups", len(dup_phones),
       [f"{d['_id']}: {d['count']} clients (ids: {d['ids'][:5]})" for d in dup_phones])


# --- Employees -----------------------------------------------------------
section("Employees — V1 requires name NOT NULL")
empty_emp_name = list(employees.find(
    {"$or": [{"name": None}, {"name": ""}]},
    {"id": 1}
))
report("employees with empty/null name", len(empty_emp_name),
       [f"id={e.get('id')}" for e in empty_emp_name])


# --- Services: unique name + NOT NULL name -------------------------------
section("Services — V1 requires name NOT NULL and unique (organization_id, lower(name))")
empty_svc_name = list(services_col.find(
    {"$or": [{"name": None}, {"name": ""}]},
    {"id": 1}
))
report("services with empty/null name", len(empty_svc_name),
       [f"id={s.get('id')}" for s in empty_svc_name])

dup_svc_names = list(services_col.aggregate([
    {"$match": {"name": {"$ne": None, "$ne": ""}}},
    {"$group": {"_id": {"$toLower": "$name"}, "count": {"$sum": 1}, "ids": {"$push": "$id"}}},
    {"$match": {"count": {"$gt": 1}}},
]))
report("duplicate service names (case-insensitive)", len(dup_svc_names),
       [f"{d['_id']!r}: ids={d['ids']}" for d in dup_svc_names])


# --- Appointment time/date validation + FK refs --------------------------
TIME_RE = re.compile(r"^T?(\d{1,2}):(\d{2})$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def parse_minutes(t):
    if not t:
        return None
    m = TIME_RE.match(t.strip())
    if not m:
        return None
    h, mi = int(m.group(1)), int(m.group(2))
    if h > 23 or mi > 59:
        return None
    return h * 60 + mi


def is_valid_date(d):
    if not d or not DATE_RE.match(d):
        return False
    try:
        datetime.strptime(d, "%Y-%m-%d")
        return True
    except ValueError:
        return False


client_ids = {c["id"] for c in clients.find({}, {"id": 1}) if c.get("id") is not None}
employee_ids = {e["id"] for e in employees.find({}, {"id": 1}) if e.get("id") is not None}
service_ids = {s["id"] for s in services_col.find({}, {"id": 1}) if s.get("id") is not None}


def audit_appointments(col, label):
    bad_time_format = []
    bad_time_order = []
    bad_date = []
    empty_name = []
    orphan_client = []
    orphan_employee = []
    orphan_service = []
    no_services = []

    for a in col.find({}):
        aid = a.get("id")
        st = parse_minutes(a.get("startTime"))
        et = parse_minutes(a.get("endTime"))
        if st is None or et is None:
            bad_time_format.append(
                f"id={aid}, startTime={a.get('startTime')!r}, endTime={a.get('endTime')!r}"
            )
        elif et <= st:
            bad_time_order.append(
                f"id={aid}, date={a.get('date')}, start={a.get('startTime')}, end={a.get('endTime')}"
            )

        if not is_valid_date(a.get("date")):
            bad_date.append(f"id={aid}, date={a.get('date')!r}")

        if not a.get("name"):
            empty_name.append(f"id={aid}")

        cid = a.get("clientId")
        if cid is not None and cid not in client_ids:
            orphan_client.append(f"appt id={aid}, clientId={cid}")

        eid = a.get("employeeId")
        if eid is not None and eid not in employee_ids:
            orphan_employee.append(f"appt id={aid}, employeeId={eid}")

        svc_list = a.get("services") or []
        if not svc_list:
            no_services.append(f"id={aid}")
        for sid in svc_list:
            if sid not in service_ids:
                orphan_service.append(f"appt id={aid}, service id={sid}")

    section(f"{label} — validation")
    report("endTime <= startTime (would violate CHECK ends_at > starts_at)",
           len(bad_time_order), bad_time_order)
    report("unparseable startTime or endTime", len(bad_time_format), bad_time_format)
    report("unparseable date", len(bad_date), bad_date)
    report("missing customer name (NOT NULL violation)", len(empty_name), empty_name)
    report("orphan clientId reference", len(orphan_client), orphan_client)
    report("orphan employeeId reference", len(orphan_employee), orphan_employee)
    report("orphan service id in services[]", len(orphan_service), orphan_service)
    report("appointments with empty services[] (informational)", len(no_services), no_services)


audit_appointments(appointments, "Appointments")
audit_appointments(archived_appointments, "ArchivedAppointments")


# --- Refresh tokens ------------------------------------------------------
section("RefreshToken — V1 keys by user_id, so each username must resolve")
usernames = {u["username"] for u in users.find({}, {"username": 1}) if u.get("username")}
orphan_tokens = [
    f"_id={t.get('_id')}, username={t.get('username')!r}"
    for t in refresh_tokens.find({}, {"username": 1})
    if t.get("username") not in usernames
]
report("refresh tokens with no matching user", len(orphan_tokens), orphan_tokens)


# --- Summary -------------------------------------------------------------
section("Summary")
if issue_categories == 0:
    print("  No blockers found. Migration script can assume clean data.")
else:
    print(f"! {issue_categories} category(ies) of issues need a decision before writing the migration.")
    print("  For each: clean it up in Mongo first, relax the V1 constraint, or have the migrator drop/log the rows.")
