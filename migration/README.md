# MongoDB -> PostgreSQL migration

One-off scripts for cutting the app over from MongoDB to PostgreSQL. Delete
this directory after the cutover ships.

## Scripts

- `audit_mongo.py` — read-only. Scans the source MongoDB and reports every row
  that would block the migration against the V1 Postgres schema (NOT NULL
  violations, unique-constraint violations, CHECK violations, unparseable
  dates/times, orphan foreign-key references).
- `migrate_mongo_to_postgres.py` — the actual migration. Wipes the target
  Postgres in a single transaction and reinserts every row from Mongo.
  Idempotent: same input always produces the same output. Skips
  `RefreshToken` (ephemeral auth state, regenerated on next login) and
  `Counters` (replaced by UUIDs entirely). Existing Mongo users are not
  carried over — a single new owner user is created from env vars.

## Workflow

1. Run `audit_mongo.py` against prod MongoDB and resolve the blockers it
   surfaces — either by cleaning the source data, relaxing a V1 constraint,
   or planning for the migrator to drop/log the offending rows.
2. Run `migrate_mongo_to_postgres.py` against a local Postgres (Compose
   stack) using a prod MongoDB snapshot. Verify the result, iterate.
3. When confident: stop writes to MongoDB, run the migration script **once**
   against the prod Postgres on Render, flip the app's request path to JPA,
   redeploy.

## Setup

```sh
cd migration
cp .env.example .env  # fill in MONGO_URI
uv sync
```

### Environment variables (infrastructure)

- `MONGO_URI` — source MongoDB connection string (required)
- `POSTGRES_URL` — target Postgres URL in `postgresql://user:pass@host:port/db`
  form, not the `jdbc:` form the API uses (required)
- `MONGO_DB` — default `Nail-Art`

### CLI arguments (per-run inputs)

Org and owner identity are passed as args so each run is explicit. See
`--help` for the full list. Required: `--org-name`, `--owner-username`.
Optional: `--org-phone`, `--org-timezone` (default `America/New_York`),
`--owner-email`, `--owner-password` (prompted interactively if omitted, which
keeps the plaintext out of shell history).

## Running

Audit (read-only, no Postgres needed):

```sh
uv run python audit_mongo.py
```

Dry-run migration (runs inside a transaction, rolls back at the end, prints
the row counts that would result):

```sh
uv run python migrate_mongo_to_postgres.py \
  --org-name "Nail Art & Spa LLC." \
  --org-phone "330-758-6633" \
  --owner-username nailart \
  --dry-run
```

Real migration (commits):

```sh
uv run python migrate_mongo_to_postgres.py \
  --org-name "Nail Art & Spa LLC." \
  --org-phone "330-758-6633" \
  --owner-username nailart
```

You'll be prompted for the owner password. The migration `TRUNCATE`s every
application table with `CASCADE` before inserting, so rerunning against the
same target is safe and produces an identical result (modulo new UUIDs). The
transaction means partial failures roll back cleanly — Postgres is never
left half-migrated.
