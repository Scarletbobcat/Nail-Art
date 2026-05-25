import os
import subprocess
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import psycopg
import pytest


SCRIPT = Path(__file__).with_name("ArchiveAppointments.py")


@pytest.fixture
def db_url() -> str:
    url = os.environ.get("POSTGRES_URL")
    if not url:
        pytest.fail("POSTGRES_URL must point to a Postgres database for archive appointment tests")
    return url


def psycopg_url(db_url: str) -> str:
    if db_url.startswith("jdbc:postgresql://"):
        return db_url.removeprefix("jdbc:")
    return db_url


def run_archive_appointments(db_url: str | None) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.pop("MONGO_URI", None)
    if db_url is None:
        env.pop("POSTGRES_URL", None)
    else:
        env["POSTGRES_URL"] = db_url
    return subprocess.run([sys.executable, str(SCRIPT)], check=False, text=True, capture_output=True, env=env)


def insert_org_employee(cur) -> tuple[str, str]:
    org_id = str(uuid.uuid4())
    employee_id = str(uuid.uuid4())
    cur.execute(
        "insert into organizations (id, name) values (%s, %s)",
        (org_id, f"archive-test-{org_id}"),
    )
    cur.execute(
        """
        insert into employees (id, organization_id, name)
        values (%s, %s, %s)
        """,
        (employee_id, org_id, "Archive Test Employee"),
    )
    return org_id, employee_id


def insert_appointment(cur, org_id: str, employee_id: str, ends_at: datetime, archived_at: datetime | None = None) -> str:
    appointment_id = str(uuid.uuid4())
    starts_at = ends_at - timedelta(hours=1)
    cur.execute(
        """
        insert into appointments (
            id,
            organization_id,
            employee_id,
            starts_at,
            ends_at,
            customer_name,
            archived_at
        )
        values (%s, %s, %s, %s, %s, %s, %s)
        """,
        (appointment_id, org_id, employee_id, starts_at, ends_at, "Archive Test Client", archived_at),
    )
    return appointment_id


def fetch_archived_at(db_url: str, appointment_id: str) -> datetime | None:
    with psycopg.connect(psycopg_url(db_url)) as conn:
        with conn.cursor() as cur:
            cur.execute("select archived_at from appointments where id = %s", (appointment_id,))
            row = cur.fetchone()
            return row[0] if row else None


@pytest.fixture
def cleanup_orgs(db_url: str):
    org_ids: list[str] = []
    yield org_ids
    if not org_ids:
        return
    with psycopg.connect(psycopg_url(db_url)) as conn:
        with conn.cursor() as cur:
            cur.execute("delete from appointments where organization_id = any(%s)", (org_ids,))
            cur.execute("delete from employees where organization_id = any(%s)", (org_ids,))
            cur.execute("delete from organizations where id = any(%s)", (org_ids,))


def seed_appointment(
    db_url: str,
    cleanup_orgs: list[str],
    *,
    days_old: int,
    archived_at: datetime | None = None,
) -> tuple[str, str]:
    ends_at = datetime.now(timezone.utc) - timedelta(days=days_old)
    with psycopg.connect(psycopg_url(db_url)) as conn:
        with conn.cursor() as cur:
            org_id, employee_id = insert_org_employee(cur)
            appointment_id = insert_appointment(cur, org_id, employee_id, ends_at, archived_at)
            cleanup_orgs.append(org_id)
            return org_id, appointment_id


def test_happyPath_archives30DayOldAppointment(db_url: str, cleanup_orgs: list[str]) -> None:
    _org_id, appointment_id = seed_appointment(db_url, cleanup_orgs, days_old=31)

    result = run_archive_appointments(db_url)

    assert result.returncode == 0, result.stderr
    archived_at = fetch_archived_at(db_url, appointment_id)
    assert archived_at is not None
    assert datetime.now(timezone.utc) - archived_at < timedelta(minutes=1)


def test_29DayOldAppointment_notArchived(db_url: str, cleanup_orgs: list[str]) -> None:
    _org_id, appointment_id = seed_appointment(db_url, cleanup_orgs, days_old=29)

    result = run_archive_appointments(db_url)

    assert result.returncode == 0, result.stderr
    assert fetch_archived_at(db_url, appointment_id) is None


def test_alreadyArchived_idempotent(db_url: str, cleanup_orgs: list[str]) -> None:
    original_archived_at = datetime.now(timezone.utc) - timedelta(days=7)
    _org_id, appointment_id = seed_appointment(db_url, cleanup_orgs, days_old=31, archived_at=original_archived_at)

    result = run_archive_appointments(db_url)

    assert result.returncode == 0, result.stderr
    assert fetch_archived_at(db_url, appointment_id) == original_archived_at


def test_loopsAcrossOrgs(db_url: str, cleanup_orgs: list[str]) -> None:
    _org_a, appointment_a = seed_appointment(db_url, cleanup_orgs, days_old=31)
    _org_b, appointment_b = seed_appointment(db_url, cleanup_orgs, days_old=31)

    result = run_archive_appointments(db_url)

    assert result.returncode == 0, result.stderr
    assert fetch_archived_at(db_url, appointment_a) is not None
    assert fetch_archived_at(db_url, appointment_b) is not None


def test_postgresUrlUnset_exitsNonZero() -> None:
    result = run_archive_appointments(None)

    assert result.returncode != 0
    assert "POSTGRES_URL" in result.stderr
