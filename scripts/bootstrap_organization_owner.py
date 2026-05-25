import argparse
import os
import sys

import bcrypt
import psycopg
from psycopg import errors


VALID_ROLES = {"owner", "admin", "staff"}


class BootstrapError(Exception):
    pass


def postgres_url(cli_url: str | None) -> str:
    url = cli_url or os.getenv("POSTGRES_URL")
    if not url:
        raise BootstrapError("POSTGRES_URL is required or pass --db-url")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create the first owner user for an existing organization."
    )
    parser.add_argument("--db-url", default=None, help="Postgres URL; defaults to POSTGRES_URL")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--org-name", required=True)
    parser.add_argument("--role", default="owner")
    return parser.parse_args()


def require_role(role: str) -> str:
    normalized = role.lower()
    if normalized not in VALID_ROLES:
        raise BootstrapError("role must be one of: owner, admin, staff")
    return normalized


def create_owner(db_url: str, username: str, password: str, org_name: str, role: str) -> str:
    password_hash = bcrypt.hashpw(
        password.encode("utf-8"), bcrypt.gensalt(rounds=10)
    ).decode("utf-8")

    conn = psycopg.connect(db_url)
    try:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("select id from organizations where name = %s", (org_name,))
                org_row = cur.fetchone()
                if org_row is None:
                    raise BootstrapError(f"no organization named: {org_name}")
                org_id = org_row[0]

                cur.execute("select id from users where username = %s", (username,))
                if cur.fetchone() is not None:
                    raise BootstrapError(f"username already exists: {username}")

                cur.execute(
                    """
                    insert into users (username, password_hash)
                    values (%s, %s)
                    returning id
                    """,
                    (username, password_hash),
                )
                user_id = cur.fetchone()[0]

                cur.execute(
                    """
                    insert into organization_users (organization_id, user_id, role)
                    values (%s, %s, %s)
                    """,
                    (org_id, user_id, role),
                )
                return str(user_id)
    except errors.UniqueViolation as exc:
        raise BootstrapError(f"username already exists: {username}") from exc
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    try:
        role = require_role(args.role)
        user_id = create_owner(
            postgres_url(args.db_url),
            args.username,
            args.password,
            args.org_name,
            role,
        )
    except BootstrapError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"Created user {args.username} with id {user_id} in org {args.org_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
