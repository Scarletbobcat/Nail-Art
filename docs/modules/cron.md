# Module: `cron/`

## Purpose

Python maintenance scripts that touch PostgreSQL directly. These are operational chores that don't belong in the API's request path.

## How it works

Python 3.14 managed with [`uv`](https://github.com/astral-sh/uv). The cron job:

1. Loads `POSTGRES_URL` from a sibling `.env` via `python-dotenv`.
2. Opens a `psycopg` connection.
3. Loops over every organization explicitly, because Spring/Hibernate tenant filters do not apply in this separate process.

Scripts are intended to be run by an external scheduler (cron, Render Job, etc.). The repo doesn't ship the scheduler itself.

Only one script remains.

## `ArchiveAppointments.py`
- **Schedule**: weekly (Sundays, per `README`).
- **Behavior**: opens a `psycopg` connection and sets `archived_at = now()` for each organization's appointments where `ends_at < now() - 30 days` and `archived_at IS NULL`.
- **Safety**: idempotent — already archived rows are left untouched.

Retired scripts:

- `AppointmentsSameStartEndTime.py`: replaced by the `appointments` table check constraint requiring `ends_at > starts_at`.
- `MergeDuplicateClients.py`: replaced by the per-organization unique phone index on `clients`.

## Patterns and conventions

- One responsibility per file.
- Read configuration from environment variables only — never hard-code connection strings.
- Idempotent by default.
- Use `psycopg` directly — these scripts intentionally bypass the API to avoid auth/HTTP overhead and to allow bulk operations.
- Loop over organizations inside the script; do not add a `CRON_ORG_ID` footgun for multi-tenant jobs.

## Examples and gotchas

- Run with `uv` so you get the locked Python version: `cd cron && uv run python ArchiveAppointments.py`.
- These scripts can run against prod. Confirm `POSTGRES_URL` before executing maintenance jobs.

## Maintenance & Accretion

Update this doc when a script is added, retired, or has its schedule/intent change. Note any one-off migrations here even after they've run so future maintainers know what already happened.
