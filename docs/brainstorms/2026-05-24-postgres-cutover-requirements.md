---
date: 2026-05-24
topic: postgres-cutover
---

# Postgres Cutover — Mongo Decommission and Behavior Parity

## Summary

Flip the entire app off MongoDB onto the existing V1 PostgreSQL schema on a single `postgres-migration-cutover` branch — JPA-backed API with multi-tenancy wired through the JWT, frontend updated lockstep for a clean contract break, cron scripts rationalized, and Mongo dependencies fully removed. Behavior parity is enforced by Testcontainers-backed integration tests plus a basic manual E2E pass before merge.

---

## Problem Frame

The salon currently runs on a single MongoDB Atlas replica set with a hand-rolled `Counter` collection minting sequential `long` IDs, wall-clock date/time strings on appointments, and zero notion of multi-tenancy in the application code. Conflict-detection logic relies on string concatenation parsed back into `LocalDateTime`. Two of the three cron jobs exist purely to clean up bad states (duplicate clients by phone, appointments with `startTime == endTime`) that the database itself never prevented.

A V1 Postgres schema with UUID PKs, `timestamptz` appointment timestamps, multi-tenant composite FKs (`(id, organization_id)`), and CHECK/UNIQUE constraints that make the cleanup crons structurally unnecessary has already been authored and is live in dev and prod (empty). A read-only audit of prod Mongo against that schema returned zero blockers. The migration script that wipes and reseeds Postgres from Mongo has been built, tested locally, and committed to this branch.

What remains is the largest piece of work: porting every domain entity, repository, service, controller, frontend page, and cron script onto the new model, then cutting prod over in a single coordinated step. The risk surface is behavior parity — the old code happens to work because everything is timezone-naïve and single-tenant in the same way; the new code is correct and explicit, and the two can disagree in edge cases (most notably timezone handling at "today's appointments" boundaries) unless tested deliberately.

---

## Actors

- A1. Salon owner (`nailart` post-cutover): the sole human user today; books, edits, and reviews appointments via the web app on desktop and iPad.
- A2. Solo developer: owns the codebase, executes the cutover, runs the migration script, walks the manual E2E checklist, holds the rollback decision.
- A3. Future org-scoped users (receptionists, additional staff): not provisioned this branch, but the auth/role plumbing must not paint into a corner that requires a refactor to add them later.
- A4. Cron runner: archives old appointments on a schedule; no human action required day-to-day.

---

## Key Flows

- F1. Cutover day
  - **Trigger:** Developer initiates cutover after this branch is merged-ready and CI-green.
  - **Actors:** A2.
  - **Steps:** Dry-run the migration script against prod Postgres via Render IP allowlist; verify row counts match Mongo; scale Render API service to zero so no writes land in Mongo mid-migration; commit the migration run; deploy the new API image (Mongo deps already removed); walk the manual E2E checklist; remove IP from allowlist; pause (do not delete) the Mongo Atlas cluster.
  - **Outcome:** Prod app fully serving from Postgres; Atlas cluster paused and held as a rollback escape hatch.
  - **Covered by:** R12, R13, R14, R15, R16, R17.

- F2. First login after cutover
  - **Trigger:** Salon owner opens the app post-cutover.
  - **Actors:** A1.
  - **Steps:** All prior refresh tokens are invalid (none were migrated); owner is shown the login screen; owner enters the username and the password chosen during the migration script run; receives a new JWT carrying `userId`, `organizationId`, and `role`; app loads `/me`, populates the user/org context, and renders the calendar.
  - **Outcome:** Owner is logged in and the app is fully usable.
  - **Covered by:** R7, R8, R18.

- F3. Booking an appointment (round-trip)
  - **Trigger:** Salon owner creates or edits an appointment from any device, regardless of that device's timezone.
  - **Actors:** A1.
  - **Steps:** Owner picks a date and a time on the form; the frontend converts that wall-clock input into the org's timezone offset before sending; the API persists a `timestamptz`; the API returns the same instant; the frontend renders it back as the same wall-clock time using the org's timezone, never the browser's.
  - **Outcome:** "9:30 AM Tuesday" entered on the form displays as "9:30 AM Tuesday" everywhere it appears, regardless of device timezone.
  - **Covered by:** R5, R9, R10.

- F4. Vertical-slice development sequence
  - **Trigger:** Developer begins the API/frontend rewrite within this branch.
  - **Actors:** A2.
  - **Steps:** Each domain area is taken end-to-end before the next begins — entity, repository, service, controller, frontend pages, and tests for that domain land together. Order: Employees → Services → Clients → Appointments → Auth. Build never sits in a state where "no feature works" for more than a single slice.
  - **Outcome:** Reviewable, demoable progress at each slice boundary; auth slice last because it touches every other surface.
  - **Covered by:** R19.

---

## Requirements

**Data migration**

- R1. The migration script in `migration/` is the sole mechanism for moving data from Mongo to Postgres. It runs idempotently (TRUNCATE CASCADE then reinsert in a single transaction) and may be re-executed any number of times before final cutover without leaving Postgres in a partial state.
- R2. The migration discards existing Mongo users (`dev`, `nailart`) and existing refresh tokens. Exactly one organization and one owner user are created from CLI arguments at run time.
- R3. The audit script in `migration/` remains in the branch as the verification tool for re-running against prod Mongo as a final pre-cutover check.

**Backend cutover**

- R4. Every domain entity is migrated from Spring Data Mongo to JPA on the V1 Postgres schema. The `Counter` entity is removed; UUIDs from the schema replace it entirely.
- R5. Appointment time handling moves from wall-clock string concatenation parsed into `LocalDateTime` to real `OffsetDateTime`/`Instant` values throughout the service layer. Conflict detection uses interval comparison, not string equality. No backend code calls `LocalDateTime.now()` or `Instant.now()` without specifying the org's timezone.
- R6. Multi-tenancy is enforced at the ORM layer: every authenticated query is filtered by the requesting user's `organizationId`. Client-supplied org IDs are never trusted. Forgetting to filter a query is not possible — the filter is declared once per entity and applied automatically per session from the authenticated principal.
- R7. JWT claims carry `userId`, `organizationId`, and `role`. Login resolves the user's organization via the `organization_users` join and stamps both into the issued token. Refresh tokens are keyed by `userId`, not username.
- R8. A `/me` endpoint returns the authenticated user (id, username) plus their organization (id, name, timezone, business phone). The frontend stores this in a React context populated at app load.
- R9. The API's request/response contract is broken cleanly: numeric IDs become UUID strings; appointment shape changes from `{date, startTime, endTime}` strings to `{startsAt, endsAt}` ISO 8601 timestamps with offset. No compatibility shim is added for the old shape.

**Frontend**

- R10. All appointment timestamp rendering goes through dayjs with the `utc` and `timezone` plugins, always with `.tz(orgTz)` applied before formatting. The org timezone is read from the user/org React context, never hard-coded, and never derived from the browser's local timezone.
- R11. Form input that takes a wall-clock time (appointment date + start/end time) is converted to ISO 8601 with the org's timezone offset before being sent to the API. Round-trip from form submit to display preserves the same wall-clock value regardless of the device's locale.

**Cron**

- R12. `ArchiveAppointments.py` is ported from pymongo to psycopg and continues to run on the same schedule against Postgres.
- R13. `AppointmentsSameStartEndTime.py` and `MergeDuplicateClients.py` are deleted. The V1 schema's `CHECK (ends_at > starts_at)` and `UNIQUE (organization_id, phone_number) WHERE phone_number <> ''` constraints make these classes of bad data impossible at the database level.

**Testing**

- R14. Repository tests run against a real PostgreSQL 16 instance (Testcontainers) — not H2 — so `timestamptz`, `citext`, and `gen_random_uuid()` behave as they will in prod.
- R15. Every existing service-layer unit test is rewritten against the JPA repositories; mocks replace the Mongo-stubbed ones. Controllers gain MockMvc-style integration tests for at least the create, edit, delete, and read paths of every domain (none exist today).
- R16. Time-handling tests cover at minimum: form-input-to-display round-trip; server-vs-salon "today" boundary in the late-evening window; basic two-appointment overlap detection; archive cutoff at the 30-day boundary. DST and midnight-crossing scenarios are deliberately not tested — they are not reachable for nail-salon business hours.

**Cutover and decommission**

- R17. Before the real prod migration run, a dry-run executes against prod Postgres (transaction rolled back) and its row counts are compared to prod Mongo's. Discrepancies block the real run.
- R18. On the real cutover, the Render API service is scaled to zero before the migration script runs, preventing in-flight Mongo writes from being lost. The new API image (with Mongo dependencies removed) is only deployed after the migration commits successfully.
- R19. When this branch merges, `spring-boot-starter-data-mongodb`, all `@Document` entities, Mongo configuration classes, and `*_MONGO_URI` environment variables are gone from the repo and from Render. The Mongo Atlas cluster is paused (not deleted) on cutover day.
- R20. Documentation under `docs/` (README, `docs/reference/local-development.md`, anything else referencing Mongo) is updated in this branch to reflect the Postgres-only state.

**Rollback**

- R21. Until the Atlas cluster is deleted, rollback is performed by reverting the cutover merge and redeploying the prior API image. The migration script is read-only on Mongo, so rollback requires no Mongo restore.

---

## Acceptance Examples

- AE1. **Covers R5, R10, R11.** Given the salon timezone is `America/New_York`, when the owner books an appointment at 9:30 AM on May 26 from an iPad with its system timezone set to `America/Los_Angeles`, the API stores `2026-05-26T13:30:00Z`, the calendar grid on that iPad renders the appointment at the 9:30 AM slot, and the appointment detail view shows "9:30 AM – 10:30 AM".
- AE2. **Covers R5, R16.** Given an existing appointment from 10:00–11:00 on May 26, when the owner attempts to create another appointment for the same employee at 10:30–11:30 on May 26, the API rejects it with a conflict response; when the second appointment is 11:00–12:00, the API accepts it.
- AE3. **Covers R5, R16.** Given the Render server runs in UTC and the salon is in `America/New_York`, when the owner opens the calendar at 11:30 PM ET (which is 3:30 AM the next day in UTC), the "today" view shows the current ET calendar date's appointments, not the next day's.
- AE4. **Covers R6.** Given a future second organization is added directly to the database, when a user authenticated to organization A issues any API request, the response contains zero rows belonging to organization B.
- AE5. **Covers R1, R17.** Given the migration script has already been run against a target Postgres, when it is re-executed with the same Mongo source, the resulting row counts in Postgres are identical and no unique-constraint or FK violations occur.
- AE6. **Covers R2, F2.** Given the migration has been committed against prod, when the salon owner first visits the app afterward, the existing browser session is invalid, the login screen is shown, and authenticating with the new owner credentials grants access.

---

## Success Criteria

- The salon owner walks through a basic manual E2E pass on cutover day — login, view today, book a new appointment, edit it, delete it, search by phone — and every step works without surprise.
- After cutover, the codebase contains no Mongo dependencies, no `@Document` entities, no `*_MONGO_URI` environment variables, and the Render dashboard shows the Mongo env vars removed.
- Automated test suite is green and covers the JPA repositories against real Postgres, the service layer with mocked repos, and the controller layer with integration-style tests.
- Two of the three cron scripts are deleted; the third runs cleanly against Postgres on the same schedule it ran on against Mongo.
- A future developer reading the API code cannot find a query path that returns rows from an organization other than the authenticated user's, even by trying.

---

## Scope Boundaries

- Platform/superuser admin (the developer's "see all tenants" capability for support) is deferred to a separate follow-up branch. No `is_superuser` column, no impersonation, no audit log this branch.
- Multi-user-per-org admin UI is not built. The schema supports `owner`/`admin`/`member` roles; the JWT carries the role claim; but no screens are added to invite, list, edit, or remove org members.
- Postgres Row Level Security is not adopted as a second isolation layer. The ORM-level filter is the single enforcement point.
- No DTO/compatibility layer is built to preserve the old API contract. Frontend and backend change shape together in this branch.
- Refresh tokens are not migrated. All users (the one) re-login after cutover.
- Second-tenant onboarding, organization-management UI, billing, and account self-service are out of scope.
- DST-transition and midnight-crossing time tests are out of scope — not reachable for nail-salon business hours.
- Mongo Atlas cluster deletion is out of scope for this branch. The cluster is paused on cutover day and deleted in a separate change weeks later.
- Connection pool tuning, observability beyond Spring defaults, and structured logging improvements are out of scope.

---

## Key Decisions

- **One-branch ship unit.** Backend, frontend, cron, and Mongo decommission all land together. Rationale: simpler mental model, single cutover, no period during which the prod app is "half on Postgres," and avoids carrying a DTO compatibility shim that exists only to be deleted later.
- **Clean API contract break over compatibility shim.** Solo developer owns both ends; throwaway adapter code has negative value. Lockstep frontend update is the lower-total-cost path.
- **JWT-claim multi-tenancy with ORM-level filter.** Future-proofs for a second tenant at near-zero cost over hardcoded-single-org. ORM filter is single-enforcement-point so forgetting `WHERE organization_id =` becomes impossible. RLS is overkill at this stage but its absence here does not preclude adding it later.
- **`/me` for display, JWT claims for authorization.** Standard SaaS pattern. Frontend never cracks the JWT; backend never trusts client-supplied identity.
- **Vertical-slice sequencing.** Each domain ships end-to-end before the next. Avoids the failure mode of "rewrote six entities, now nothing works for three days." Auth slice last because it touches every other surface.
- **Real-Postgres integration tests.** H2 lies about `timestamptz`, `citext`, and `gen_random_uuid()` in ways that would invalidate parity tests. Testcontainers cost is acceptable given the migration's risk profile.
- **Pragmatic timezone test scope.** Test what is reachable for the business (round-trip, server-tz boundary, conflict detection, archive cutoff). Skip what isn't (DST, midnight). Earlier dramatization of DST risk was wrong for this domain.
- **Cron rationalization, not just porting.** Two of three scripts exist only because the schema didn't prevent the bad states they cleaned up. The V1 schema does prevent them, so those scripts are deleted, not ported.
- **Atlas paused, not deleted.** A few weeks of paid-but-idle cluster is cheap insurance for a cutover of this size.

---

## Dependencies / Assumptions

- The V1 Flyway migration (`V1__init_multi_org_schema.sql`) is the authoritative target schema and is already deployed to both dev and prod Postgres. No schema changes are introduced in this branch beyond what V1 already provides.
- Render's external Postgres URL and IP allowlist remain the mechanism for the developer to run the migration script from a laptop.
- The audit script's clean result against prod Mongo (zero blockers) holds at cutover time. A re-audit immediately before the real run is the verification.
- Render is the only deployment target. No staging environment exists; the dev/prod split is "Docker Compose locally" vs "Render."
- dayjs remains the frontend date library. The `utc` and `timezone` plugins ship with the existing dayjs version — no new dependency added.
- The salon operates only in `America/New_York` and stores that as the organization's timezone. No multi-timezone-per-org logic is required.

---

## Outstanding Questions

### Resolve Before Planning

*(none — all scope-shaping questions resolved in dialogue.)*

### Deferred to Planning

- [Affects R5][Technical] Exact JPA type choice for appointment timestamps (`OffsetDateTime` vs `Instant` vs `ZonedDateTime`) and how it surfaces in JSON serialization. All three round-trip through `timestamptz` correctly; the choice is ergonomic.
- [Affects R6][Technical] Whether the ORM-level filter is wired via Hibernate's `@Filter` + a Spring interceptor, an AOP aspect on repository methods, or a custom JPA repository base interface. All three achieve the requirement; pick at planning time based on which integrates best with Spring Security's principal resolution.
- [Affects R7][Technical] JWT signing/secret rotation strategy on cutover — same `JWT_SECRET_KEY` as today (existing tokens already invalidated by user model change, so rotation gives no extra benefit) vs new key as belt-and-suspenders. Lean: keep the existing key.
- [Affects R10, R11][Needs research] Whether to introduce an ESLint rule enforcing `.tz(orgTz)` on dayjs format chains, or rely on a single helper module + code review. Tied to how often the rule would actually catch a regression.
- [Affects R15][Technical] Test fixture strategy across Testcontainers test classes — shared container with TRUNCATE between tests vs container per class vs container per test. Performance vs isolation tradeoff settled at planning time.
- [Affects R12][Technical] Whether the ported `ArchiveAppointments` cron continues to run via the existing scheduler (the `cron/` directory and whatever invokes it today) or moves into Spring's `@Scheduled` on the Java side. Either works.
