# Module: `cron/`

## Purpose

Python maintenance scripts that touch MongoDB directly. These are operational chores that don't belong in the API's request path: archival, data-hygiene checks, and one-off migrations.

## How it works

Python 3.14 managed with [`uv`](https://github.com/astral-sh/uv). Each script:

1. Loads `MONGO_URI` from a sibling `.env` via `python-dotenv`.
2. Opens a `pymongo.MongoClient`.
3. Operates on the `Nail-Art` database (matches the API's collections).

Scripts are intended to be run by an external scheduler (cron, Render Job, etc.). The repo doesn't ship the scheduler itself.

## Scripts

### `ArchiveAppointments.py`
- **Schedule**: weekly (Sundays, per `README`).
- **Behavior**: moves rows from `Appointments` where `date < today-14d` into `ArchivedAppointments`, then deletes archived rows where `date < today-30d`. Updated from "everything before today" to "older than two weeks" in `17ba870`.
- **Safety**: idempotent — running twice on the same day moves nothing extra.

### `AppointmentsSameStartEndTime.py`
- **Schedule**: ad-hoc.
- **Behavior**: reports any appointments where `startTime == endTime`. Backstop for the server-side validation added in `5fc7690`. Read-only.

### `MergeDuplicateClients.py`
- **Schedule**: one-off migration, kept for historical reference.
- **Behavior**: merges duplicate `Clients` by phone number, re-syncs the `Clients` counter, and was paired with the unique partial index added in `0257cf8`. Don't re-run without reading the script — it mutates data.

## Patterns and conventions

- One responsibility per file.
- Read configuration from environment variables only — never hard-code connection strings.
- Idempotent by default. If a script is destructive, name it after its action (e.g. `MergeDuplicateClients`) and require a manual run.
- Use `pymongo` directly — these scripts intentionally bypass the API to avoid auth/HTTP overhead and to allow bulk operations.

## Examples and gotchas

- Run with `uv` so you get the locked Python version: `cd cron && uv run python ArchiveAppointments.py`.
- The scripts assume the `Nail-Art` database name. If a future deployment uses a different name, parameterize via env var rather than editing the script.
- These scripts can run against prod. Confirm `MONGO_URI` before executing destructive scripts.

## Maintenance & Accretion

Update this doc when a script is added, retired, or has its schedule/intent change. Note any one-off migrations here even after they've run so future maintainers know what already happened.
