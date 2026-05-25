import os
import sys
from uuid import UUID

import psycopg
from dotenv import load_dotenv


ARCHIVE_CUTOFF_DAYS = 30


class ArchiveAppointmentsError(Exception):
    pass


def postgres_url() -> str:
    load_dotenv()
    url = os.getenv("POSTGRES_URL")
    if not url:
        raise ArchiveAppointmentsError("POSTGRES_URL is required")
    if url.startswith("jdbc:postgresql://"):
        return url.removeprefix("jdbc:")
    return url


def organization_ids(conn: psycopg.Connection) -> list[UUID]:
    with conn.cursor() as cur:
        cur.execute("select id from organizations order by id")
        return [row[0] for row in cur.fetchall()]


def archive_org_appointments(conn: psycopg.Connection, org_id: UUID) -> int:
    with conn.cursor() as cur:
        cur.execute(
            """
            update appointments
            set archived_at = now(),
                updated_at = now()
            where organization_id = %s
              and archived_at is null
              and ends_at < now() - (%s * interval '1 day')
            """,
            (org_id, ARCHIVE_CUTOFF_DAYS),
        )
        return cur.rowcount


def archive_appointments(db_url: str) -> int:
    total_archived = 0
    with psycopg.connect(db_url) as conn:
        with conn.transaction():
            for org_id in organization_ids(conn):
                archived_count = archive_org_appointments(conn, org_id)
                total_archived += archived_count
                print(f"org_id={org_id} archived={archived_count}")
    return total_archived


def main() -> int:
    try:
        total_archived = archive_appointments(postgres_url())
    except (ArchiveAppointmentsError, psycopg.Error) as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"total_archived={total_archived}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
