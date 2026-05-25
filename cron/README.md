# Cron Jobs

This directory contains the maintenance job that runs outside the Spring API.

## ArchiveAppointments

`ArchiveAppointments.py` is scheduled weekly on Sunday. It reads `POSTGRES_URL`
from the environment, loads a sibling `.env` via `python-dotenv`, loops over
every organization, and soft-archives appointments whose `ends_at` timestamp is
older than 30 days by setting `archived_at`.

Run it from this directory:

```sh
uv run python ArchiveAppointments.py
```

The old MongoDB maintenance scripts were removed during the Postgres cutover:

- `AppointmentsSameStartEndTime.py`: replaced by the `appointments` table check
  constraint requiring `ends_at > starts_at`.
- `MergeDuplicateClients.py`: replaced by the per-organization unique phone
  index on `clients`.
