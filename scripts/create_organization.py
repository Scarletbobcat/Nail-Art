import argparse
import os
import sys

import psycopg
from psycopg import errors


class CreateOrganizationError(Exception):
    pass


def postgres_url(cli_url: str | None) -> str:
    url = cli_url or os.getenv("POSTGRES_URL")
    if not url:
        raise CreateOrganizationError("POSTGRES_URL is required or pass --db-url")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create an organization, default settings, and its unavailability marker service."
    )
    parser.add_argument("--db-url", default=None, help="Postgres URL; defaults to POSTGRES_URL")
    parser.add_argument("--name", "--org-name", dest="org_name", required=True)
    parser.add_argument("--timezone", default="America/New_York")
    parser.add_argument("--business-phone", default=None)
    parser.add_argument("--unavailability-service-name", default="Unavailable")
    parser.add_argument("--sms-reminders-enabled", action="store_true")
    return parser.parse_args()


def create_organization(
    db_url: str,
    org_name: str,
    timezone: str,
    business_phone: str | None,
    unavailability_service_name: str,
    sms_reminders_enabled: bool,
) -> str:
    conn = psycopg.connect(db_url)
    try:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("select id from organizations where name = %s", (org_name,))
                if cur.fetchone() is not None:
                    raise CreateOrganizationError(f"organization already exists: {org_name}")

                cur.execute(
                    """
                    insert into organizations (name, timezone, business_phone)
                    values (%s, %s, %s)
                    returning id
                    """,
                    (org_name, timezone, business_phone),
                )
                org_id = cur.fetchone()[0]

                cur.execute(
                    """
                    insert into organization_settings (organization_id, sms_reminders_enabled)
                    values (%s, %s)
                    """,
                    (org_id, sms_reminders_enabled),
                )

                cur.execute(
                    """
                    insert into services (organization_id, name, is_unavailability_marker)
                    values (%s, %s, true)
                    """,
                    (org_id, unavailability_service_name),
                )
                return str(org_id)
    except errors.UniqueViolation as exc:
        raise CreateOrganizationError(f"organization already exists: {org_name}") from exc
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    try:
        org_id = create_organization(
            postgres_url(args.db_url),
            args.org_name,
            args.timezone,
            args.business_phone,
            args.unavailability_service_name,
            args.sms_reminders_enabled,
        )
    except CreateOrganizationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"Created organization {args.org_name} with id {org_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
