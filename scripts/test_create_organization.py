import os
import subprocess
import sys
import uuid
from pathlib import Path

import psycopg
import pytest


SCRIPT = Path(__file__).with_name("create_organization.py")


@pytest.fixture
def db_url() -> str:
    url = os.environ.get("POSTGRES_URL")
    if not url:
        pytest.fail("POSTGRES_URL must point to a Postgres database for create organization script tests")
    return url


@pytest.fixture
def org_name() -> str:
    return f"create-org-test-{uuid.uuid4()}"


def run_create_organization(
    db_url: str,
    org_name: str,
    *,
    sms_reminders_enabled: bool = False,
) -> subprocess.CompletedProcess[str]:
    command = [
        sys.executable,
        str(SCRIPT),
        "--db-url",
        db_url,
        "--org-name",
        org_name,
        "--unavailability-service-name",
        "Unavailable",
    ]
    if sms_reminders_enabled:
        command.append("--sms-reminders-enabled")
    return subprocess.run(command, check=False, text=True, capture_output=True)


def fetch_org_bundle(db_url: str, org_name: str):
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute("select id, name from organizations where name = %s", (org_name,))
            org = cur.fetchone()
            if org is None:
                return None
            org_id = org[0]
            cur.execute(
                "select organization_id, sms_reminders_enabled from organization_settings where organization_id = %s",
                (org_id,),
            )
            settings = cur.fetchone()
            cur.execute(
                """
                select organization_id, name, is_unavailability_marker
                from services
                where organization_id = %s
                """,
                (org_id,),
            )
            services = cur.fetchall()
            return org, settings, services


def count_orgs(db_url: str, org_name: str) -> int:
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute("select count(*) from organizations where name = %s", (org_name,))
            return cur.fetchone()[0]


def test_happy_path_creates_three_rows(db_url: str, org_name: str) -> None:
    result = run_create_organization(db_url, org_name)

    assert result.returncode == 0, result.stderr
    bundle = fetch_org_bundle(db_url, org_name)
    assert bundle is not None
    org, settings, services = bundle
    org_id, stored_name = org
    assert stored_name == org_name
    assert settings == (org_id, False)
    assert services == [(org_id, "Unavailable", True)]


def test_duplicate_org_name_fails_cleanly(db_url: str, org_name: str) -> None:
    first = run_create_organization(db_url, org_name)
    second = run_create_organization(db_url, org_name)

    assert first.returncode == 0, first.stderr
    assert second.returncode != 0
    assert "organization already exists" in second.stderr
    assert count_orgs(db_url, org_name) == 1


def test_smsRemindersEnabled_flag_propagatesToSettings(db_url: str, org_name: str) -> None:
    result = run_create_organization(db_url, org_name, sms_reminders_enabled=True)

    assert result.returncode == 0, result.stderr
    _org, settings, _services = fetch_org_bundle(db_url, org_name)
    assert settings[1] is True
