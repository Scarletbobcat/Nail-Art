import os
import subprocess
import sys
import uuid
from pathlib import Path

import bcrypt
import psycopg
import pytest


SCRIPT = Path(__file__).with_name("bootstrap_organization_owner.py")


@pytest.fixture
def db_url() -> str:
    url = os.environ.get("POSTGRES_URL")
    if not url:
        pytest.fail("POSTGRES_URL must point to a Postgres database for bootstrap script tests")
    return url


@pytest.fixture
def org_name() -> str:
    return f"bootstrap-test-{uuid.uuid4()}"


def run_bootstrap(db_url: str, username: str, password: str, org_name: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--db-url",
            db_url,
            "--username",
            username,
            "--password",
            password,
            "--org-name",
            org_name,
        ],
        check=False,
        text=True,
        capture_output=True,
    )


def create_org(db_url: str, org_name: str) -> uuid.UUID:
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                insert into organizations (name, timezone, business_phone)
                values (%s, 'America/New_York', '555-0100')
                returning id
                """,
                (org_name,),
            )
            return cur.fetchone()[0]


def fetch_user(db_url: str, username: str):
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute(
                "select id, username, password_hash from users where username = %s",
                (username,),
            )
            return cur.fetchone()


def count_users(db_url: str, username: str) -> int:
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute("select count(*) from users where username = %s", (username,))
            return cur.fetchone()[0]


def fetch_membership(db_url: str, organization_id: uuid.UUID, user_id: uuid.UUID):
    with psycopg.connect(db_url) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                select organization_id, user_id, role
                from organization_users
                where organization_id = %s and user_id = %s
                """,
                (organization_id, user_id),
            )
            return cur.fetchone()


def test_happy_path_creates_owner(db_url: str, org_name: str) -> None:
    org_id = create_org(db_url, org_name)
    username = f"owner-{uuid.uuid4()}"
    password = "correct horse battery staple"

    result = run_bootstrap(db_url, username, password, org_name)

    assert result.returncode == 0, result.stderr
    assert f"Created user {username}" in result.stdout
    assert f"in org {org_name}" in result.stdout

    user_id, stored_username, password_hash = fetch_user(db_url, username)
    assert stored_username == username
    assert bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))

    membership = fetch_membership(db_url, org_id, user_id)
    assert membership == (org_id, user_id, "owner")


def test_duplicate_username_fails_cleanly(db_url: str, org_name: str) -> None:
    create_org(db_url, org_name)
    username = f"owner-{uuid.uuid4()}"
    password = "correct horse battery staple"

    first = run_bootstrap(db_url, username, password, org_name)
    second = run_bootstrap(db_url, username, password, org_name)

    assert first.returncode == 0, first.stderr
    assert second.returncode != 0
    assert f"username already exists: {username}" in second.stderr
    assert count_users(db_url, username) == 1


def test_nonexistent_org_fails_cleanly(db_url: str) -> None:
    username = f"owner-{uuid.uuid4()}"
    org_name = f"missing-{uuid.uuid4()}"

    result = run_bootstrap(db_url, username, "correct horse battery staple", org_name)

    assert result.returncode != 0
    assert f"no organization named: {org_name}" in result.stderr
    assert count_users(db_url, username) == 0
