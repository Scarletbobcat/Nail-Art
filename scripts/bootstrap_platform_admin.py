import argparse
import os
import sys

import bcrypt
import psycopg
from psycopg import errors


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
        description=(
            "Create the first platform-admin user. A platform admin is org-less "
            "(no organization_users membership, no role); its authority comes from "
            "the is_platform_admin flag. Use this to bootstrap the operator console."
        )
    )
    parser.add_argument("--db-url", default=None, help="Postgres URL; defaults to POSTGRES_URL")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--email", default=None, help="Optional contact email for the admin account")
    return parser.parse_args()


def create_platform_admin(db_url: str, username: str, password: str, email: str | None) -> str:
    password_hash = bcrypt.hashpw(
        password.encode("utf-8"), bcrypt.gensalt(rounds=10)
    ).decode("utf-8")

    conn = psycopg.connect(db_url)
    try:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("select id from users where username = %s", (username,))
                if cur.fetchone() is not None:
                    raise BootstrapError(f"username already exists: {username}")

                cur.execute(
                    """
                    insert into users (username, email, password_hash, is_platform_admin)
                    values (%s, %s, %s, true)
                    returning id
                    """,
                    (username, email, password_hash),
                )
                return str(cur.fetchone()[0])
    except errors.UniqueViolation as exc:
        raise BootstrapError(f"username already exists: {username}") from exc
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    try:
        user_id = create_platform_admin(
            postgres_url(args.db_url),
            args.username,
            args.password,
            args.email,
        )
    except BootstrapError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"Created platform admin {args.username} with id {user_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
