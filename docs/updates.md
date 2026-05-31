# Documentation Updates

Accretive log of significant documentation changes. Append to the top.

## 2026-05-30 — Per-device refresh sessions

- Updated auth docs to describe hashed opaque refresh tokens, independent multi-device sessions, and current-session logout behavior.

## 2026-05-25 — PostgreSQL cutover runbook

- Added [`docs/operations/postgres-cutover-runbook.md`](operations/postgres-cutover-runbook.md), a checklist for dry-run, migration, deploy, manual E2E, rollback, and Atlas cleanup.

## 2026-05-25 — PostgreSQL cutover documentation refresh

- Updated module and reference docs for the PostgreSQL-only runtime: JPA/Flyway, UUID IDs, tenant context, `useMe()`/`orgTz`, psycopg cron, and cutover-era auth/bootstrap guidance.

## 2026-05-25 — Local PostgreSQL migration groundwork

- Documented the local PostgreSQL Compose service and API datasource env vars during the migration groundwork.

## 2026-05-24 — Initial docs suite

- Added `docs/INDEX.md`, `docs/reference/` (architecture, conventions, lessons, local-development, deployment), `docs/modules/` (client, api, cron).
- Added root `AGENTS.md` router and `CLAUDE.md` pointer.
- Rewrote `README.md` to lead with what the app is, refresh the screenshots, and link into `docs/`.
- Refreshed all screenshots under `images/` against the current UI (calendar, search, employees, services, clients, login, modals).

## Maintenance & Accretion

Append a dated entry whenever a docs change is non-trivial: new module doc, restructured INDEX, retired guidance, etc. Keep entries one to three lines.
