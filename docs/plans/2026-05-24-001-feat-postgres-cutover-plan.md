---
title: "feat: Postgres cutover — Mongo decommission and behavior parity"
type: feat
status: active
date: 2026-05-24
deepened: 2026-05-25
reviewed: 2026-05-25 (round 4)
origin: docs/brainstorms/2026-05-24-postgres-cutover-requirements.md
---

# feat: Postgres cutover — Mongo decommission and behavior parity

## Summary

Land the full Mongo→Postgres cutover on `postgres-migration-cutover` in 12 dependency-ordered implementation units: test harness first, then the identity/multi-tenancy foundation (JWT claims + `/me` + Hibernate `@TenantId`), then four domain vertical slices (Employees → Services → Clients → Appointments), then the Appointments frontend, cron rationalization, Mongo decommission, docs refresh, and a cutover-day operational runbook.

---

## Problem Frame

The codebase is fully on MongoDB with no notion of multi-tenancy in the application layer, numeric IDs from a hand-rolled `Counter` collection, and timezone-naïve wall-clock string fields on appointments. The V1 Postgres schema (UUIDs, `timestamptz`, multi-tenant composite FKs, CHECK/UNIQUE constraints) is already deployed; the migration script that wipes and reseeds Postgres from Mongo has been built, tested locally, and committed. What remains is the largest piece of the work — porting every domain entity/repo/service/controller, the frontend pages that consume them, and the cron scripts, then cutting prod over in a single coordinated step. Behavior parity is the risk surface; the existing code is timezone-naïve and single-tenant in the same convenient way, the new code is correct and explicit, and the two can disagree at edges (most notably timezone handling at "today" boundaries) unless tested deliberately. Full context lives in the origin requirements doc.

---

## Requirements

**Data migration**
- R1. Migration script (`migration/`) remains the sole data-movement mechanism; runs idempotently inside a single transaction (see origin: `docs/brainstorms/2026-05-24-postgres-cutover-requirements.md`).
- R2. Existing Mongo users and refresh tokens are discarded; exactly one organization and one owner user created from CLI args.
- R3. Audit script (`migration/audit_mongo.py`) survives in the branch as the final pre-cutover verification tool.

**Backend cutover**
- R4. Every domain entity migrates from Spring Data Mongo to JPA on the V1 Postgres schema; `Counter` and `CounterService` are deleted.
- R5. Appointment time handling moves from wall-clock string concatenation parsed into `LocalDateTime` to `OffsetDateTime` throughout the service layer; conflict detection uses interval comparison; no backend code calls timezone-less `now()`.
- R6. Multi-tenancy is enforced at the ORM layer; every authenticated query is filtered by the requesting user's `organizationId`; client-supplied org IDs are ignored.
- R7. JWT claims carry `userId`, `organizationId`, and `role`; refresh tokens key by `userId` (not username).
- R8. `/users/me` returns the authenticated user plus their organization (id, name, timezone, business phone); frontend hydrates it via a TanStack Query `useMe()` hook (`useQuery(['me'])`) populated at app load. No separate React Context is introduced — overrides origin's "React context" phrasing for fit with the existing TanStack-Query-only state pattern (see Key Technical Decisions).

**Frontend**
- R9. API contract breaks cleanly: numeric IDs → UUID strings; appointment shape `{date, startTime, endTime}` strings → `{startsAt, endsAt}` ISO 8601 with offset. No compat shim.
- R10. All appointment timestamp rendering goes through dayjs with the `utc`/`timezone` plugins, always `.tz(orgTz)` applied before formatting; org timezone comes from the `useMe()` hook, never hard-coded or browser-derived.
- R11. Form input wall-clock time is converted to ISO 8601 with the org's timezone offset before send; round-trip preserves the same wall-clock display regardless of device locale.

**Cron**
- R12. `cron/ArchiveAppointments.py` is ported to psycopg and continues to run on its existing schedule.
- R13. `cron/AppointmentsSameStartEndTime.py` and `cron/MergeDuplicateClients.py` are deleted; the V1 schema's CHECK and UNIQUE constraints make them structurally unnecessary.

**Testing**
- R14. Repository tests run against real PostgreSQL 16 via Testcontainers; H2 is not used.
- R15. Existing service-layer unit tests are rewritten against JPA repositories; controller-layer integration tests (currently absent) are added via MockMvc for the create/edit/delete/read paths of every domain.
- R16. Time-handling tests cover: form-to-display round-trip, server-vs-salon "today" boundary, basic conflict overlap, archive cutoff. DST and midnight scenarios are deliberately excluded.

**Cutover and decommission**
- R17. Final cutover begins with a dry-run against prod Postgres; discrepancies block the real run.
- R18. Render API service is scaled to zero before the real migration runs; new API image (Mongo-free) is deployed only after migration commits.
- R19. When the branch merges, `spring-boot-starter-data-mongodb`, all `@Document` entities, Mongo configuration, `mongodb-driver-sync` pin, and `*_MONGO_URI` env vars are gone from the repo and from Render. Atlas cluster is paused, not deleted.
- R20. `docs/` (README, `AGENTS.md`, `docs/INDEX.md`, `docs/modules/api.md`, `docs/modules/client.md`, `docs/modules/cron.md`, `docs/reference/architecture.md`, `docs/reference/conventions.md`, `docs/reference/lessons.md`, `docs/reference/local-development.md`, `docs/reference/deployment.md`, `docs/updates.md`) reflect the Postgres-only state.

**Rollback**
- R21. Rollback is "revert the cutover merge + redeploy prior API image"; migration is read-only on Mongo.

**Origin actors:** A1 (salon owner), A2 (solo developer), A3 (future org-scoped users — plumbing only), A4 (cron runner)

**Origin flows:** F1 (cutover day), F2 (first login after cutover), F3 (booking round-trip), F4 (vertical-slice dev sequence)

**Origin acceptance examples:** AE1 (tz round-trip across devices, covers R5/R10/R11), AE2 (conflict detection, covers R5/R16), AE3 (server-tz "today" boundary, covers R5/R16), AE4 (cross-org isolation, covers R6), AE5 (migration idempotency, covers R1/R17), AE6 (post-cutover first login, covers R2/F2)

---

## Scope Boundaries

- Platform/superuser admin (the developer's "see all tenants" capability) — separate follow-up branch.
- Multi-user-per-org admin UI / org-management screens / invite flow. *(Note: the `POST /users` endpoint ships in this branch, but with no UI surface — invocation is curl-only via the runbook. The admin UI lights it up.)*
- Postgres Row Level Security as a second isolation layer. (The 2026-05-25 deepening pass briefly added RLS in scope as a "structural backstop" but the 2026-05-25 document review reversed that — see Key Technical Decisions and Alternative Approaches Considered. For solo dev + 1-2 tenants, `@TenantId` alone is structurally sufficient; the deepening pass's RLS expansion accumulated carve-outs faster than it added correctness.)
- DTO/compatibility layer to preserve the old API shape.
- Migrating refresh tokens.
- Salon-2 billing, account self-service, admin UI for org/staff management. (Salon-2 *org provisioning* — creating the `organizations` row, `organization_settings` row, and "Unavailable" service — ships in this branch via `scripts/create_organization.py`, see U5. What's deferred: a UX for onboarding, a billing surface, anything self-service. Routine staff addition routes through the owner-gated `POST /users` endpoint via curl until the admin UI follow-up branch lands.)
- DST-transition and midnight-crossing time tests — not reachable for nail-salon business hours.
- Mongo Atlas cluster deletion — paused only.
- Connection pool tuning, observability/metrics beyond Spring defaults, structured logging improvements.
- Moving the SMS scheduler off Spring `@Scheduled` to anywhere else.
- **Universal/system services (`services.organization_id IS NULL`).** Considered for the "Unavailable" employee-time-off service so it could be shared across all tenants. Rejected because it would weaken the V1 schema's composite FK `appointment_services → services(id, organization_id)` — the structural guarantee that you can't link org-A appointments to org-B services. Instead, every org gets its own "Unavailable" service row identified by a `is_unavailability_marker BOOLEAN` column (see Key Technical Decisions, U5, U8).

### Deferred to Follow-Up Work

- **Long-term replacement for the "Unavailable" service concept** — e.g., a first-class `time_off` table with its own UI rendering. The marker-flag approach is the minimum-scope cutover solution; a richer model can come later.
- **Per-tenant Twilio credential storage.** Today's SMS scheduler reads Twilio credentials from env vars (one set, shared). When salon 2 onboards, they'll need their own Twilio number — that storage and the SMS service's per-org credential lookup is documented in `docs/brainstorms/2026-05-24-salon-2-prep-notes.md` and lands in a follow-up branch.

---

## Context & Research

### Relevant Code and Patterns

- `api/src/main/java/com/nail_art/appointment_book/entities/` — current 7 `@Document` entities; UUID/JPA replacements live in the same package, renaming the same files in place.
- `api/src/main/java/com/nail_art/appointment_book/repositories/` — current `MongoRepository<T, Long>` interfaces; replaced by `JpaRepository<T, UUID>`.
- `api/src/main/java/com/nail_art/appointment_book/services/AppointmentService.java` — current conflict-check logic with `LocalDateTime.parse(date + startTime)`; rewrite uses interval comparison on `OffsetDateTime`. Preserve the existing semantics: "no overlap for same employee on same calendar date."
- `api/src/main/java/com/nail_art/appointment_book/services/SmsService.java` — `@Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")` and `getAppointmentsForTomorrow`. Time math becomes salon-tz-aware; reminder dispatch path unchanged.
- `api/src/main/java/com/nail_art/appointment_book/configs/JwtAuthenticationFilter.java` — claim parsing; refactor to extract `userId` (UUID) + `organizationId` (UUID) + `role` and stamp them on the principal.
- `api/src/main/java/com/nail_art/appointment_book/configs/SecurityConfiguration.java` — preserve `/auth/**` as the only anonymous path (`docs/reference/lessons.md` auth invariant).
- `api/src/main/java/com/nail_art/appointment_book/controllers/UserController.java` — `/users/me` exists today returning `User` entity; reshape response, no consumer in frontend today.
- `api/src/main/java/com/nail_art/appointment_book/services/AuthenticationService.java` — login/register/refresh; rewrite against JPA, JWT claim shape changes.
- `api/src/main/resources/db/migration/V1__init_multi_org_schema.sql` — authoritative target schema; no V2 in this plan.
- `client/src/api/api.ts` — shared axios with refresh interceptor; preserve interceptor behavior, only the JWT contents change (frontend never cracks the JWT).
- `client/src/AppointmentsPage/Calendar/Calendar.tsx`, `client/src/AppointmentsPage/Calendar/components/MobileCalendar.tsx`, `client/src/AppointmentsPage/Calendar/components/CalendarHeader.tsx`, `client/src/AppointmentsPage/Search/Search.tsx` — dayjs uses `dayjs(app.date + app.startTime)` (browser-tz implicit); refactor to `dayjs(app.startsAt).tz(orgTz)`.
- `client/src/api/<resource>/` modules — per-resource API shapes; ID and appointment field renames cascade here.
- `cron/ArchiveAppointments.py` — port from pymongo to psycopg; logic stays "older than N days → archive."

### Institutional Learnings

- **`docs/reference/lessons.md` — auth invariants:** refresh-cookie `maxAge=30d`, `SameSite=None; Secure`; `findByUsernameIgnoreCase` everywhere (the V1 schema uses `citext` for username, so case-insensitivity is automatic at DB level — no `IgnoreCase` repository method needed). After profile/cookie changes, clearing localStorage is required — relevant on cutover (all users get logged out anyway).
- **`docs/reference/lessons.md` — appointments:** `markReminderSent` MUST NOT route through `editAppointment` because the latter triggers conflict checks. Preserve as a separate non-conflict-checking service method. The "end > start" backstop is now a CHECK constraint (no longer needs cron).
- **`docs/reference/lessons.md` — frontend:** search is partial/case-insensitive everywhere (regex on Mongo today; ILIKE or `containsIgnoreCase` derivation on JPA). Pagination cap is `2000` on `/clients` — frontend and backend must match.
- **`docs/reference/lessons.md` — operations:** `mongodb-driver-sync` was pinned for Render SSL handshake; pin is no longer needed once Mongo deps are removed.
- **`AGENTS.md`** — page directories PascalCase, `axios` instance only for HTTP, service-layer tests under `com.nail_art.appointment_book.services` are what CI runs, `cd client && npm run build` before committing FE. All preserved; AGENTS.md itself updates in U10 (not U11) to drop the Counter and Mongo-pin notes so the router does not give actively-wrong guidance during U2–U9.

### External References

External research skipped — Spring Boot + JPA + Hibernate `@TenantId` + Testcontainers + JWT + dayjs `timezone` plugin are all well-trodden, with clear local patterns to follow once introduced. (`@Filter` was initially considered but rejected; the final design uses Hibernate 6 `@TenantId` discriminator-based tenancy, no RLS — see Key Technical Decisions.) Re-evaluate if a unit's implementation surfaces an unfamiliar concern.

---

## Key Technical Decisions

- **Sequencing: auth/identity ships first as the foundation.** Origin F4 listed "Auth last because it touches every other surface," but that argument inverts — auth touching every surface is exactly why making it the foundation is cheaper. Every later slice consumes the real authenticated principal and the real Hibernate filter instead of scaffolding that has to be removed.
- **JPA timestamp type: `OffsetDateTime`.** Maps to `timestamptz` cleanly; Jackson serializes to ISO 8601 with offset by default; preserves offset metadata for the frontend. `Instant` discards offset (unwanted for UX); `ZonedDateTime` adds a zone name that we don't carry.
- **Multi-tenancy enforcement: Hibernate 6 `@TenantId` only (application layer).** The original plan's `@Filter` + `HandlerInterceptor` design was unsound (filter doesn't cover native queries / `findById` / criteria; `HandlerInterceptor` runs before the Session exists given `open-in-view=false`; `@Scheduled` jobs bypass the interceptor entirely). `@TenantId` is the Hibernate 6 replacement and does cover those surfaces: it participates in standard JPA query rewriting and applies to `EntityManager.find()`, derived queries, and criteria queries. The 2026-05-25 deepening pass briefly added Postgres RLS as a second layer; the same-day document review reversed that decision. Reasons: (a) for solo dev + 1-2 tenants who writes and reviews 100% of the code, the realistic threat model is `@TenantId` wiring bugs (caught by AE4 cross-org integration test in Phase 2) and missed `TenantContext.runAs` in scheduled jobs (RLS doesn't catch these either — it just changes "wrong tenant's data" into "zero rows"); (b) RLS accumulated carve-outs (`organizations` and `users` exempted, login bootstrap special case, migration script bypass via owner role, role split, four new env vars, DataSource proxy for `SET LOCAL`, GUC handling for unset cases) faster than it added correctness; (c) `@Query(nativeQuery=true)` is a real bypass risk in principle but currently absent from the repo and easy to spot in code review. The design now: `@TenantId` on every tenant-scoped entity, `TenantContext` ThreadLocal populated by a `OncePerRequestFilter` (web) and explicit `runAs` wrapping in scheduled jobs, Hibernate `CurrentTenantIdentifierResolver` reads from `TenantContext`. No Postgres roles split, no V2 migration, no RLS policies, no DataSource proxy. See Alternative Approaches Considered for the full rejection rationale.
- **Frontend user/org state: TanStack Query `useMe()` hook, not a separate React Context.** TanStack Query is already the only server-state mechanism in the codebase; adding Context just to mirror what `useQuery(['me'])` already gives you duplicates the pattern. Hook reads cache, components subscribe naturally.
- **Test isolation: shared Testcontainer per test class with `@Transactional` rollback per test method.** Spring Boot's standard pattern; container startup cost amortized; transactional rollback gives clean isolation without TRUNCATE between tests.
- **JWT signing key: keep existing `JWT_SECRET_KEY`.** UUID `sub` replacing numeric one invalidates every existing token anyway, so rotation gives no incremental security benefit.
- **Timezone-discipline enforcement: single helper module + code review.** ESLint custom rule would be overkill for a one-developer codebase; convention plus the helper is sufficient.
- **Cron scheduler: keep external Python scheduler.** Don't migrate `ArchiveAppointments` to Spring `@Scheduled` — keeps cron concerns in one place, minimizes churn.
- **`/users/me` response shape: reshape to `{user: {...}, organization: {...}}`.** No existing frontend consumer, so this is a safe-to-change endpoint. Update happens in U2 alongside the JWT claim change.
- **`citext` for username eliminates `findByUsernameIgnoreCase`.** V1 schema declares username as `citext`; JPA repository methods can use `findByUsername` directly and case-insensitivity is automatic.
- **Search remains partial/case-insensitive.** Mongo regex matchers become Spring Data derived queries (`findByNameContainingIgnoreCase`, `findByPhoneNumberContaining`) or `ILIKE` parameters; semantic parity preserved.
- **Unique-phone violation maps to HTTP 409.** Today's `DuplicateKeyException` → 409 mapping shifts to `DataIntegrityViolationException` → 409 in `GlobalExceptionHandler` (same user-visible behavior).
- **Conflict-detection logic: keep "no overlap for same employee on same calendar date" semantics, but operate on intervals.** New rule expressible as `existing.starts_at < new.ends_at AND existing.ends_at > new.starts_at AND existing.employee_id = new.employee_id` — exclude the appointment being edited by ID.
- **`markReminderSent` stays a dedicated service method.** Direct field update; no conflict re-check. Preserves the lesson from `docs/reference/lessons.md`.
- **`POST /auth/register` is removed post-cutover; replaced by owner-token-gated `POST /users`.** Anonymous self-registration would create accounts with no `organization_users` row (confused state). Instead the new endpoint reads `organizationId` from the authenticated principal and attaches the new user to the caller's org — solves the org-scoping concern without the operator friction of a CLI-only path. The first salon's owner is seeded by the migration script; first owners of new orgs (when the deferred onboarding work lands) come from `scripts/bootstrap_organization_owner.py`. Everything else (routine staff additions, role changes, password resets) routes through the auth-gated endpoint. Forward-compatible with the deferred platform-admin role: that role will extend the endpoint to accept an explicit `organizationId` in the body, no rework. (The 2026-05-25 deepening pass originally swapped the endpoint for a `scripts/create_user.py` CLI; reversed during same-day document review because the CLI created laptop-bottleneck friction at a load-bearing onboarding moment.)
- **"Unavailable" service is per-tenant, identified by a marker column.** Each org gets its own `Unavailable` service row with `is_unavailability_marker = true`. The owner can rename it to "Off" / "PTO" / anything; the flag stays. Frontend specials-color any appointment whose `services` array contains a service with the flag set. New V3 Flyway migration adds the column + a partial unique constraint (`UNIQUE (organization_id) WHERE is_unavailability_marker`) so each org has exactly one. Chosen over (a) a NULL/system-org service that would weaken the composite FK `appointment_services → services(id, organization_id)`, and (b) trusting the owner not to rename a name-keyed row. See U5 + U8.

---

## Open Questions

### Resolved During Planning

- *JPA timestamp type:* `OffsetDateTime` — see Key Technical Decisions.
- *Multi-tenancy enforcement mechanism:* Hibernate `@TenantId` only — see Key Technical Decisions. (Original brainstorm rejected RLS; the 2026-05-25 deepening pass briefly added RLS as a dual-layer backstop; the same-day document review reversed it as scope creep for solo dev + 1-2 tenants.)
- *Frontend state for `/me`:* TanStack Query hook, no Context — see Key Technical Decisions.
- *Testcontainer fixture strategy:* shared per class + transactional rollback — see Key Technical Decisions.
- *JWT key rotation:* keep existing key (no rotation at cutover; revisit only on suspected exposure) — see Key Technical Decisions.
- *Cron scheduler:* keep external Python — see Key Technical Decisions.
- *Sequencing of auth slice:* moved to first (U2), with explicit Phase 1 exit criteria — see Key Technical Decisions and Phased Delivery → Phase 1.
- *`POST /auth/register` post-cutover:* deleted; replaced by owner-token-gated `POST /users` endpoint (revised 2026-05-25 document review). Bootstrap CLI retained only for first-owner-of-new-org case via `scripts/bootstrap_organization_owner.py`. See Key Technical Decisions and U2.
- *"Unavailable" service identification:* per-tenant `is_unavailability_marker BOOLEAN` column on `services` (V3 Flyway migration); rejected universal/system-org approach because it would weaken the composite FK enforcement — see Key Technical Decisions, U5, and Scope Boundaries.
- *Pagination cap 2000 enforcement:* controller-side `Math.min(size, 2000)` per `ClientController` precedent — see U6.
- *MUI picker timezone:* `timezone={orgTz}` on each picker component (`<DatePicker>`, `<TimePicker>`, `<DateTimePicker>`, etc.) via the `TimezoneProps` mixin — NOT on `<LocalizationProvider>` (verified against installed `@mui/x-date-pickers@8.27.2`: the prop is declared in `models/timezone.d.ts` and not in `LocalizationProvider.d.ts`). See U8.

### Deferred to Implementation

- Exact Hibernate filter parameter type — `UUID` vs `String`. Likely `UUID` for type safety, but may surface a Hibernate quirk worth knowing.
- Whether `OrganizationSettings` is its own JPA entity with a `@OneToOne` from `Organization`, or modeled as an `@Embeddable` on `Organization`. Schema has it as a separate table; pick at implementation time based on Spring's idiom.
- Concrete JPQL/SQL for `getAppointmentsForTomorrow` once `LocalDate.now(salonZone)` semantics are exercised against real data.
- Whether the dayjs helper lives in `client/src/utils/datetime.ts` as standalone functions or as a thin hook (`useFormatTime()` that reads `orgTz` from `useMe()` internally). Pick by ergonomics during U8.
- Final shape of the controller integration test base class — separate `@WebMvcTest`-style tests per controller, or one `@SpringBootTest` base with MockMvc shared across.

### From Round 1 review — all resolved 2026-05-25

The two Critical items below were resolved in the 2026-05-24 deepening pass. The remaining 12 items were triaged and resolved in the 2026-05-25 user-driven pass. Items 1–4 and 7 were rejected as solo-dev / single-operator ops theater — at this scale (one developer running cutover from their personal laptop, with sole control over what merges to main) the recommended ceremonies bought process without security gain (see memory: `feedback_solo_dev_right_sizing`). Items 5, 6, 8, 9, 10, 11, and 12 were folded into the affected units.

- ~~[Critical] Hibernate filter activation mechanism may not be sound as designed.~~ **Resolved 2026-05-25 (revised same day).** Switched to Hibernate 6 `@TenantId` discriminator-based tenancy. The intermediate dual-layer `@TenantId` + RLS design was reversed during document review as scope creep for solo dev + 1-2 tenants; `@TenantId` alone covers `EntityManager.find()`, derived queries, and criteria queries (the failure modes of the rejected `@Filter`). See U3 redesign, Key Technical Decisions, and Alternative Approaches Considered.

- ~~[Critical] SMS scheduler `@Scheduled` runs outside HTTP request context.~~ **Resolved 2026-05-25.** `SmsService.sendReminders()` iterates orgs (loop of size 1 today; expanding to N when the second salon onboards) and wraps each org's body in `TenantContext.runAs(orgId, ...)` so `CurrentTenantIdentifierResolver` sees the right tenant for `@TenantId`-discriminated queries. Per-appointment transaction granularity preserves existing failure semantics (Twilio failure on one appointment doesn't roll back others). The per-tenant Twilio credential storage that the loop will need at N>1 is deliberately deferred to a follow-up branch documented in `docs/brainstorms/2026-05-24-salon-2-prep-notes.md`. See U7 SmsService approach.

- ~~[High] Migration `--dry-run` semantics against prod Postgres unspecified.~~ **Resolved (no change).** The `--dry-run` path in `migration/migrate_mongo_to_postgres.py` already wraps the entire migration in a single transaction with explicit `conn.rollback()` (line 220). Postgres TRUNCATE is transactional; a dropped connection without commit also rolls back. The proposed post-cutover re-run guard (`--cutover-started-at` sentinel) was rejected as solo-dev theater — the user knows not to re-run the migration script after cutover. U12 runbook will state plainly: "the migration script is destructive in commit mode; only run during the cutover window."

- ~~[High] Render Postgres external URL exposure window has no time bound or auto-revoke.~~ **Resolved (no change).** Solo-dev operator will manually remove the allowlist entry after cutover. No formal time limit or auto-revoke script. U12 runbook includes the remove step as a checkpoint item.

- ~~[Medium] External Postgres URL credentials not rotated post-cutover.~~ **Resolved (no change).** Personal-laptop shell history is not a meaningful exposure surface at this scale. U3's role split (`nail_art_owner` for migration, `nail_art_runtime` for app, neither with `BYPASSRLS`) is the meaningful defense; password rotation after personal-laptop use was solo-dev theater.

- ~~[Medium] JWT signing key rotation procedure absent.~~ **Resolved (no change).** `JWT_SECRET_KEY` has never been exposed; rotation at this scale buys no security. Keep existing key; revisit if/when a key-exposure incident occurs.

- **[Medium] `/auth/register` post-cutover access policy.** **Resolved → folded into U2.** Endpoint disabled; routine user creation goes through the owner-gated `POST /users` endpoint; first-owner-of-new-org bootstrap goes through `scripts/bootstrap_organization_owner.py`. See U2 file list and Key Technical Decisions. (Round 1 originally proposed a broader `scripts/create_user.py` CLI; reversed during same-day document review in favor of the endpoint + narrow bootstrap CLI.)

- **[Medium] Search regex parity.** **Resolved → folded into U6 test scenarios.** Read of `ClientService.searchClients` confirmed: current Mongo behavior uses `Criteria.where(...).regex(input, "i")` with no formatting normalization. A search for "555-12" against stored "(555) 123-4567" does NOT match today. JPA `findByPhoneNumberContaining` preserves actual current behavior. U6 test scenarios updated to assert literal substring matching, not phone-formatting normalization. No `phone_digits` generated column needed; no service-layer normalization.

- ~~[Medium] One-branch rollback fragility — no `main` embargo policy.~~ **Resolved (no change).** Solo dev controls all merges to `main`; no other commits will land during the cutover window because the user is the only one who can push. Rollback path is "revert the cutover merge OR reset main to the prior known-good commit." Formal embargo policy and precondition assertion were solo-dev theater.

- **[Medium] Auth-first sequencing risk argument not directly rebutted.** **Resolved → folded into Phased Delivery.** Phase 1 now has an explicit exit criterion: principal-shape contract frozen + structural cross-tenant test passing against User/OrganizationUser entities before Phase 2 begins. See Phased Delivery → Phase 1.

- **[Low] Pagination cap 2000 enforcement mechanism.** **Resolved → folded into U6.** Controller-side `Math.min(size, 2000)` per `ClientController.searchClients` (already the pattern in the current code, line 57). No global `PageableHandlerMethodArgumentResolverCustomizer` — one endpoint needs the cap, one line of code.

- **[Low] MUI picker timezone configuration.** **Resolved → folded into U8 (corrected round 1 2026-05-25b).** The `timezone` prop is on the picker components (via `TimezoneProps`), NOT on `<LocalizationProvider>` — verified against installed `@mui/x-date-pickers@8.27.2`. Each picker call site passes `timezone={orgTz}`. See U8 file list.

- **[Low] `isServiceType3` magic-number color rule replacement.** **Resolved → folded into U5 + U8 + new V3 Flyway migration.** Approach: add `services.is_unavailability_marker BOOLEAN DEFAULT FALSE` column via a new V3 Flyway migration; partial unique constraint `UNIQUE (organization_id) WHERE is_unavailability_marker` enforces one-per-org; migration script sets the flag on the seeded "Unavailable" service; frontend uses `services.find(s => s.isUnavailabilityMarker)` instead of name lookup. Applies to both MobileCalendar and the desktop calendar (per owner's clarification — the special-color rule should be on both). See U5 and U8.

- **[Low advisory] Ungrouped Implementation Units don't carry the thematic Phase grouping cleanly.** **Resolved → folded into Implementation Units section structure.** Added `### Phase 1: Foundation`, `### Phase 2: Domain vertical slices`, `### Phase 3: Cleanup and decommission` subheaders inside `## Implementation Units` to mirror Phased Delivery.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

### Authenticated request flow (post-cutover)

```mermaid
sequenceDiagram
    actor Owner as Salon Owner
    participant SPA as React SPA
    participant API as Spring API
    participant Filter as JwtAuthFilter
    participant Interceptor as TenantContextWebFilter
    participant Session as Hibernate Session
    participant DB as Postgres

    Owner->>SPA: Open app
    SPA->>API: POST /auth/login (username, password)
    Note over API,DB: anonymous path; TenantContext unset; User/OrganizationUser are bootstrap entities (no @TenantId)
    API->>DB: SELECT user + org via organization_users
    DB-->>API: user (UUID) + org (UUID, tz)
    API-->>SPA: JWT { sub=userId, org=orgId, role } + refresh cookie
    SPA->>SPA: store token in localStorage
    SPA->>API: GET /users/me (Bearer token)
    Filter->>Filter: parse JWT → principal { userId, orgId, role }
    Interceptor->>Session: TenantContext.set(orgId)
    API->>DB: SELECT user + org (User unscoped; Organization fetched by id)
    DB-->>API: rows
    API-->>SPA: { user: {...}, organization: { id, name, timezone, ... } }
    SPA->>SPA: TanStack Query cache populated; useMe() now ready
    SPA->>API: GET /appointments/date/2026-05-26
    Filter->>Filter: parse JWT
    Interceptor->>Session: TenantContext.set(orgId)
    API->>DB: SELECT appointments — Hibernate auto-adds WHERE organization_id = :orgId via @TenantId discriminator
    DB-->>API: appointments for this org only
    API-->>SPA: [{ id: uuid, startsAt: "...Z", endsAt: "...Z", ... }]
    SPA->>SPA: dayjs(startsAt).tz(orgTz) for display
```

### Conflict-detection rule shape (replaces string-parsed `LocalDateTime` comparison)

```text
overlap(existing, candidate) :=
    existing.employee_id = candidate.employee_id
    AND existing.organization_id = candidate.organization_id     -- enforced by filter anyway
    AND existing.id <> candidate.id                              -- exclude self when editing
    AND existing.starts_at < candidate.ends_at
    AND existing.ends_at   > candidate.starts_at
```

The DB-level `CHECK (ends_at > starts_at)` removes the "same start and end" backstop case the cron used to detect.

### Frontend timezone discipline

```text
display: ISO with offset (from API)  ──dayjs.tz(orgTz).format(...)──▶  wall-clock string

form input: { date, time } in salon tz  ──dayjs.tz(combined, orgTz).toISOString()──▶  ISO for API
```

`orgTz` always sourced from `useMe()`. Browser timezone never read.

---

## Phased Delivery

Units are dependency-ordered, but several may run in parallel inside a phase if multiple humans were involved. Since this is solo work, treat phases as sequential checkpoints — each phase ends with a green build and a demoable surface.

### Phase 1: Foundation (cannot ship without these)

- U1 — Testcontainers harness + frontend Vitest setup
- U2 — Identity layer (User/Org/auth flow/JWT claims/`/me`/Mongo autoconfig excluded)
- U3 — Multi-tenancy enforcement: `@TenantId` + `TenantContext` + `TenantContextWebFilter`. Structural tests only at this stage; full AE4 cross-domain isolation test is the Phase 2 gate.

After this phase, the app boots on Postgres for auth with Mongo autoconfig already excluded. No domain CRUD works yet.

**Phase 1 exit criteria (must all be true before U4 begins):**
1. `POST /auth/login` returns a JWT whose decoded claims include `sub` (UUID), `org` (UUID), `role`.
2. `GET /users/me` returns `{user, organization}` with no `passwordHash` field anywhere in the response body.
3. The **principal-shape contract is frozen** — `Authentication.getPrincipal()` exposes `userId: UUID`, `organizationId: UUID`, `role: String`, and downstream code reads `organizationId` off the principal. This contract is referenced by every later domain unit's `@TenantId` wiring; any change after this point cascades into U4–U7 and must be a deliberate re-deepening of the plan.
4. `TenantContextIntegrationTest` passes — structural cross-tenant test against the real `User` / `OrganizationUser` entities from U2 (which are NOT `@TenantId`-annotated by design) verifying that anonymous/bootstrap paths work with `TenantContext` unset. Composite-key (`@EmbeddedId`) discriminator verification is deferred to U7 where the real `AppointmentServiceLink` entity exists with its Flyway-managed table — no test-only stub entity is created in `src/test/java`, since `ddl-auto=none` + Flyway V1 would leave it without a backing table. Full cross-domain `@TenantId` isolation validation runs at the Phase 2 exit gate via `CrossOrgIsolationIntegrationTest`. Phase 1 is structurally complete when bootstrap paths work + the `CurrentTenantResolver` sentinel-UUID behavior is unit-tested.

Auth-first is sequenced first specifically so that every later unit consumes a real, frozen authenticated principal and the real Hibernate `@TenantId` discriminator system — no scaffolding to remove later. The risk that the brainstorm flagged (auth bugs have 4-7 units of blast radius) is mitigated by the exit criteria above: nothing in Phase 2 runs until the auth foundation is structurally proven via `TenantContextIntegrationTest` against real Postgres with the `@TenantId` discriminator system.

### Phase 2: Domain vertical slices

- U4 — Employees end-to-end
- U5 — Services end-to-end
- U6 — Clients end-to-end (also deletes `MongoConfig.java`; `DataIntegrityViolationException` mapping)
- U7 — Appointments backend
- U8 — Appointments frontend

**Phase 2 exit gate:** `CrossOrgIsolationIntegrationTest` (added at end of U7) seeds two organizations with rows in every domain, authenticates as user A, and verifies isolation on BOTH read and write paths:
- **Reads:** GET list / GET by-id on every domain endpoint returns zero rows from org B.
- **Writes:** (a) `PUT /appointments/<org-B-uuid>` returns 404 (the appointment isn't visible under `@TenantId`, so the controller's `findById` returns empty and the update never fires); the org-B row in DB is unchanged. (b) `POST /appointments/create` with `employeeId` referencing an org-B employee fails — the composite FK `appointments(employee_id, organization_id) → employees(id, organization_id)` (V1 schema lines 108-109) makes this structurally impossible: with `organization_id` stamped by `@TenantId` as user A's org, the FK won't resolve org B's employee. Expected response 400 or 409 depending on which layer catches it first. (c) `POST /appointments/create` with a phone number that matches an org-B client follows the "look up by phone" lesson (`docs/reference/lessons.md`) — the lookup is `@TenantId`-scoped to user A's org, returns null, and a new org-A client is created (no cross-org link). (d) Symmetrically applied to Employees, Services, Clients endpoints where applicable.

This is the actual AE4 enforcement. Phase 2 does not close until this test is green.

After this phase, the app is fully functional on Postgres including the calendar, all CRUD, search, and SMS. Mongo deps are still in the pom but no code references them.

### Phase 3: Cleanup and decommission

- U9 — Cron rationalization
- U10 — Mongo decommission
- U11 — Documentation refresh
- U12 — Cutover runbook

After this phase, the branch is mergeable. Cutover happens via the U12 runbook.

> **Salon-2 readiness note.** This branch delivers Postgres parity, the auth/identity foundation, the `@TenantId` multi-tenancy plumbing, the owner-gated `POST /users` endpoint, and (folded in via U5 round-1 review 2026-05-25b) `scripts/create_organization.py` for second-org provisioning. The remaining gate for actual salon-2 onboarding is **per-tenant Twilio credential storage** — without it, SMS reminders for salon-2's clients would dispatch from salon-1's Twilio number. That work lives in a follow-up branch documented in `docs/brainstorms/2026-05-24-salon-2-prep-notes.md`. With this branch shipped, salon-2 can be provisioned at the org/user/services level immediately; SMS-on for salon-2 waits for the Twilio follow-up.

---

## Implementation Units

Phase subheaders below mirror the Phased Delivery section. They are reading aids only — the unit IDs are the load-bearing references.

### Phase 1: Foundation

- U1. **Test harness foundation**

**Goal:** Stand up Testcontainers-backed integration testing so every later unit can ship real-Postgres tests for repositories and controllers without H2 lies.

**Requirements:** R14, R15

**Dependencies:** None

**Files:**
- Modify: `api/pom.xml` (add `org.testcontainers:testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `org.springframework.boot:spring-boot-starter-test` if not present)
- Modify: `.github/workflows/ci.yml` (broaden the Maven test selector from `com.nail_art.appointment_book.services.**` to `com.nail_art.appointment_book.**` so the new `repositories.*IntegrationTest`, `controllers.*IntegrationTest`, and `configs.OrgFilterIntegrationTest` classes are actually run by CI; also add a `cd client && npm test` step gated on the new Vitest setup below — without these edits, integration tests compile but never execute, and client tests have no harness to run in)
- Create: `api/src/test/java/com/nail_art/appointment_book/PostgresIntegrationTest.java` (abstract base annotated `@SpringBootTest` + `@Testcontainers`; declares the shared Postgres 16 container as a `@Container` static field; uses `@DynamicPropertySource` to wire `spring.datasource.*` to the container; classes that extend it inherit the container)
- Create: `api/src/test/resources/application-test.properties` (test-profile overrides: `spring.jpa.hibernate.ddl-auto=none`, `spring.flyway.enabled=true` so the V1 migration runs against the container)
- Modify: `client/package.json` — add `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom` as devDependencies; add a `"test": "vitest run"` script (and `"test:watch": "vitest"` for local). Vitest commits the runner choice; "or whichever runner the client uses" elsewhere in the plan resolves to Vitest.
- Create: `client/vitest.config.ts` — jsdom environment, points at the existing tsconfig
- Create: `client/src/test-setup.ts` — imports `@testing-library/jest-dom` for matchers
- Test: scratch test file that asserts the container starts and Flyway applies V1 successfully

**Approach:**
- Single shared container per JVM (the `@Container` static field pattern with `Testcontainers#parallel()` disabled — startup cost amortized across the whole test run).
- Per-test isolation comes from `@Transactional` on each test class (Spring rolls back the test transaction after each method).
- Integration test base class is the lone point of Testcontainers contact — domain tests subclass it and add their own `@Autowired` repositories.

**Patterns to follow:**
- Existing test naming under `api/src/test/java/com/nail_art/appointment_book/services/` (e.g., `ClientServiceTest.java`) — keep one test class per service/controller; new repo/controller tests use the same package layout.
- AGENTS.md: "Add service-layer tests under `com.nail_art.appointment_book.services` for new backend behavior — CI runs that package." Verify CI picks up new repo/controller test packages too; expand the test glob in `.github/workflows/ci.yml` if it's narrower than `**`.

**Test scenarios:**
- Happy path: container starts, V1 migration applies, a sentinel `SELECT 1` returns `1` from a repository call.
- Edge case: a test class running multiple `@Test` methods sees a clean database state at the start of each method (transactional rollback verified).
- Integration: a write in one test method is not visible to a subsequent method, proving rollback isolation.

**Verification:**
- `./mvnw test -Dtest="com.nail_art.appointment_book.PostgresIntegrationTest"` succeeds.
- `.github/workflows/ci.yml` glob includes repo + controller test packages so CI fails when those tests fail.

---

- U2. **Identity layer cutover — User, Organization, auth flow, JWT claims, `/me`**

**Goal:** Replace Mongo-backed auth with JPA-backed identity. JWT issued at login carries `userId`/`organizationId`/`role`. `/users/me` returns the user + organization payload the frontend will hydrate from. Refresh-token cookie behavior is preserved exactly. Frontend gains a `useMe()` TanStack Query hook that all later UI work depends on.

**Requirements:** R2, R7, R8, F2, AE6

**Dependencies:** U1

**Files:**
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/User.java` (replace `@Document` with `@Entity @Table(name="users")`; `id` becomes `UUID`; `username` is `String` annotated `@Column(name="username", columnDefinition="citext")` so Hibernate emits the right DDL/parameter cast for the Postgres citext column; remove Mongo-specific indexes)
- Create: `api/src/main/java/com/nail_art/appointment_book/entities/Organization.java`
- Create: `api/src/main/java/com/nail_art/appointment_book/entities/OrganizationUser.java` (composite-key entity for `organization_users`; carries `role`)
- Create: `api/src/main/java/com/nail_art/appointment_book/entities/OrganizationSettings.java` (decision deferred — entity vs `@Embeddable`)
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/RefreshToken.java` (key by `user_id UUID`; remove `username` column)
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/UserRepository.java` → `JpaRepository<User, UUID>`; `findByUsername(String)` (no IgnoreCase — `citext` handles it)
- Create: `api/src/main/java/com/nail_art/appointment_book/repositories/OrganizationRepository.java`, `OrganizationUserRepository.java`
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/RefreshTokenRepository.java` → key by `UUID`; `findByUserId`, `deleteByUserId`
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/AuthenticationService.java` (login resolves user → primary org via `OrganizationUser`; stamp `organizationId` + `role` on the issued JWT)
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/JwtService.java` (add claims `org` and `role`; `sub` is now UUID string)
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/CustomUserDetailsService.java` (load via JPA; principal exposes `organizationId` and `role`)
- Modify: `api/src/main/java/com/nail_art/appointment_book/configs/JwtAuthenticationFilter.java` (parse new claims; populate principal)
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/UserController.java` → `/users/me` returns `MeResponse { user: {...}, organization: {...} }`. **Also delete the `GET /users/` handler that returns `userService.allUsers()` entirely** — it has no frontend consumer today and `User` is a bootstrap entity without `@TenantId`, so it would leak cross-tenant user rows the moment salon 2 onboards. Same rationale as the `POST /auth/register` deletion.
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/AuthenticationController.java` — **delete the `POST /auth/register` handler entirely**. Routine user creation moves to an owner-token-gated endpoint (see `UserController` modification below). `/auth/**` stays anonymous for login/refresh.
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/UserController.java` — alongside the `/users/me` change, add `POST /users` — owner-token-gated, body `{username, password, role}`. Reads `organizationId` from the authenticated principal and creates the new user + `organization_users` join row scoped to that org. Owner role required. Use `@PreAuthorize("hasAuthority('OWNER')")` (not `hasRole`) to avoid the silent `ROLE_` prefix mismatch — `hasAuthority` checks the literal granted-authority string, removing the prefix-convention pitfall. The role string from the DB ('owner') is uppercased once in `JwtAuthenticationFilter` when adding to `GrantedAuthorities` so the authority is exactly `'OWNER'`. Service layer also validates the inbound `role` against the schema enum (`owner`, `admin`, `staff`) and rejects with 400 for unrecognized values (don't surface the DB CHECK constraint as a 500). Forward-compatible with a future `platform_admin` role: when that role exists in a follow-up branch, the endpoint will branch to accept an explicit `organizationId` in the body when the caller is a platform admin; today it always uses the caller's org.
- Modify: `api/src/main/java/com/nail_art/appointment_book/configs/SecurityConfiguration.java` — confirm the removed `POST /auth/register` route doesn't leak as a 405 vs 404 anomaly; everything under `/auth/` not explicitly mapped should 404. `POST /users` requires authentication; owner-role gate enforced at the controller (`@PreAuthorize`) or via method security configuration.
- Create: `scripts/bootstrap_organization_owner.py` — small psycopg CLI taking `--username --password --org-name --role owner`. Single-purpose: creates the FIRST owner of a new organization (the only case where no prior authenticated user exists to gate creation through the endpoint). Looks up the org by name, bcrypt-hashes the password (rounds=10, matching the migration script), inserts a row into `users`, then inserts the `organization_users` join row with role `owner`. Reuses the migration script's `psycopg` + `bcrypt` deps. Refuses to create a duplicate username (`citext` unique constraint surfaces). Routine staff additions, role changes, and password resets go through the `POST /users` endpoint instead — no laptop required for the common case. The deferred `scripts/create_organization.py` (per Scope Boundaries) may absorb this script or call it; pick at follow-up time.
- Create: `api/src/main/java/com/nail_art/appointment_book/responses/MeResponse.java`
- Create: `client/src/api/me/me.ts` + `client/src/api/me/index.ts` (API caller)
- Create: `client/src/hooks/useMe.ts` (TanStack Query hook: `useQuery({ queryKey: ['me'], queryFn: fetchMe, staleTime: Infinity, refetchOnWindowFocus: false, retry: (count, err) => err?.response?.status !== 401 && count < 3 })` — `refetchOnWindowFocus: false` because the `/me` payload is essentially immutable per session; the custom `retry` predicate avoids retrying on genuine 401s but does retry transient network errors before surfacing them.)
- Modify: `client/src/Login/Login.tsx` (after `login()` resolves, call `queryClient.prefetchQuery({ queryKey: ['me'], queryFn: fetchMe })` so the data is in cache before `navigate(...)` — committed prefetch, not invalidate; closes the race window where post-redirect calendar renders with undefined `orgTz`)
- Modify: `client/src/App.tsx` — wrap all authenticated routes in a `<RequireMe>` boundary that calls `useMe()`; while `isLoading` render `<CircularLoading />`; **redirect to `/Login` only when the error is a genuine 401** (`error?.response?.status === 401`); for other errors (transient network, 5xx), keep any cached data and render the children if `data` is defined. **If retries are exhausted (`isError && !data` and the error is not a 401), render an inline error fallback — a full-page message "Unable to reach the server. Tap to retry." with a button that calls `refetch()`** — instead of an indefinite `<CircularLoading />`. This gives the owner a recovery affordance on Render free-tier cold starts or transient backend outages. This avoids bouncing an already-authenticated user to login on a WiFi blip mid-session. Downstream components rely on this gate and may treat `orgTz` as guaranteed-defined. Eliminates the per-component undefined-orgTz handling burden and forbids silent fallback to browser tz. Test scenario: after 3 failed non-401 retries with no cached data, `<RequireMe>` renders the error message + retry button, not an indefinite spinner.
- Modify: `client/src/api/auth/auth.ts` (no JWT shape change visible to frontend; verify refresh path still works)
- Modify: `client/src/api/api.ts` — add a 503 branch to the response interceptor. Today the interceptor handles 401 (refresh-or-redirect) and 403 (redirect to login); 5xx responses fall through to `Promise.reject(error)` and surface as per-component error state with no global signal. The U2 backend design (JwtAuthenticationFilter returns 503 on DB throw) requires the interceptor to NOT trigger the clear-and-redirect path on 503 and to surface a transient-error signal. Implementation: when `error.response.status === 503`, do NOT call the logout/redirect path; instead, propagate the error to TanStack Query so `<RequireMe>`'s `isError && !data` path renders the retry fallback (per the U2 `<RequireMe>` spec). For other authenticated pages that hit a 503, the per-query error state remains the local fallback — no new global toast required for this branch (a global degraded-state banner can land in a follow-up if Render free-tier blips become a recurring UX issue). The key invariant this edit enforces: **503 does NOT log users out**.
- Test: `api/src/test/java/com/nail_art/appointment_book/services/AuthenticationServiceTest.java` (rewrite from Mongo mocks to Testcontainers)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/UserControllerIntegrationTest.java` (new — MockMvc against real Postgres)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/AuthenticationControllerIntegrationTest.java` (new — login/refresh integration tests; one negative test asserting `POST /auth/register` returns 404)
- Test: extend `UserControllerIntegrationTest.java` with `POST /users` scenarios: (a) owner-token creates a new user attached to the owner's org; (b) non-owner role gets 403; (c) duplicate username returns 409; (d) explicit `organizationId` in body for a non-platform-admin caller is ignored — new user always lands in caller's org (forward-compat hardening)
- Test: `client/src/hooks/useMe.test.tsx` (frontend hook test using `@tanstack/react-query` test utilities). Add scenario: a mocked 503 response on `GET /users/me` does NOT trigger the axios interceptor's clear-and-redirect path; the hook surfaces `isError && !data` and `<RequireMe>` renders the retry fallback. A 401 still triggers the clear-and-redirect path (existing behavior preserved).
- Test: `scripts/test_bootstrap_organization_owner.py` (pytest + psycopg against dev-stack Postgres; seeds an org, creates the first owner via the script, asserts the row exists and the bcrypt hash verifies against the input password; second invocation with the same username fails cleanly)

**Approach:**
- The migration script (`migration/migrate_mongo_to_postgres.py`) already created one organization and one owner user with a bcrypt'd password, plus the row in `organization_users` linking them with role `owner`. Dev/test seed is the same shape — created via Testcontainers fixtures or a `@Sql` script.
- `JwtAuthenticationFilter` parses the JWT, extracts `userId` (UUID), `org` (UUID), `role`. **Before populating the principal, it runs `organizationUserRepository.existsByUserIdAndOrganizationId(userId, orgId)` as a server-side cross-check.** If the row doesn't exist (forged claim, revoked membership, key leak with tampered payload), the filter rejects the token with 401. **If the query throws (DB unreachable, connection pool exhausted, transient outage), the filter rejects with 503 (Service Unavailable), NOT 401** — this distinction matters: 401 triggers the SPA's clear-and-redirect-to-login path; 503 lets the axios interceptor surface a transient-error state without mass-logging-out everyone on a DB blip. On success, it constructs an `Authentication` whose principal exposes `userId`/`organizationId`/`role`. Downstream code reads `organizationId` off the principal. The check is one indexed query per authenticated request — closes the "trusted JWT claim is the only gate" gap that becomes load-bearing once RLS is out of scope. Test scenario: filter with a mocked repo that throws returns 503, not 401.
- `/auth/**` stays anonymous (login + refresh); everything else stays authenticated; CORS unchanged. (`docs/reference/lessons.md` invariant.) **`POST /auth/register` is deleted** — see Key Technical Decisions; routine user creation goes through the owner-gated `POST /users` endpoint; first-owner-of-new-org bootstrap via `scripts/bootstrap_organization_owner.py`.
- Refresh cookie: `HttpOnly; SameSite=None; Secure; maxAge=30d`. (Same lesson.) `RefreshToken` row keys by `user_id UUID`; `AuthenticationService.refresh` looks up by `userId` instead of username. **When issuing the new access token from `/auth/refresh`, re-query `organization_users` to populate the `org` and `role` claims fresh** — same claim-building path used at login. The existing controller pattern (`generateToken(userDetails)`) is NOT sufficient because `User` is a bootstrap entity that doesn't carry `organizationId` or `role`. Without this re-fetch, the new access token has stale or missing `org`/`role` claims, the next request fails the `JwtAuthenticationFilter` cross-check, and the user bounces to login. Test scenario added: after a role change in `organization_users`, a refresh issues a token reflecting the new role (not the role at original login time).
- `MeResponse` is a dedicated DTO (not the `User` JPA entity) that excludes `passwordHash` by construction. Test scenario asserts the field is absent from the JSON body.
- Frontend `useMe()` is the canonical source of `orgTz` — every later UI unit reads from it.
- **Exclude Mongo autoconfig from boot at the start of U2**, not at U10. Add `@SpringBootApplication(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })` to the main application class and remove `spring.data.mongodb.*` keys from `application.properties` / `application-dev.properties`. Without this step, the dev API will fail to boot the moment U4 begins removing `@Document` entities because Mongo auto-config still requires a reachable client. (Mongo deps stay in `pom.xml` until U10; the autoconfig exclusion just disables the boot wiring.) **Also at U2: read `MongoConfig.java` and verify it's safe to coexist with the autoconfig exclusion.** If `MongoConfig` references autoconfigured beans (`MongoTemplate`, `MongoClient`) that no longer exist after the exclusion, it must be either deleted at U2 (not waiting for U6) or refactored to not depend on the autoconfig. The U6 deletion plan assumes `MongoConfig` is harmless throughout U2-U5; verify before U2 ships.

**Patterns to follow:**
- Existing axios + TanStack Query pattern (`docs/modules/client.md`): each per-resource API module under `client/src/api/<resource>/`. Apply same shape for `client/src/api/me/`.
- Existing `LoginResponse` / `RegisterUserDto` / `TokenDto` shape (`api/src/main/java/com/nail_art/appointment_book/dtos/` and `responses/`). Add `MeResponse` alongside.
- `AGENTS.md`: "Don't loosen CORS or the JWT auth path. Only `/auth/**` is anonymous." Verify after refactor.

**Test scenarios:**
- **Covers AE6.** Happy path: `POST /auth/login` with valid creds returns a JWT whose decoded claims include `sub` (UUID matching the user row), `org` (UUID matching the organization), `role=owner`. Refresh-token cookie is set with the right attributes.
- Happy path: `GET /users/me` with a valid bearer returns `{user: {id, username, role}, organization: {id, name, timezone, businessPhone}}` matching the seeded values.
- Security gate: `GET /users/me` response JSON body does not contain a key named `passwordHash`, `password`, or any variant. Asserted explicitly to prevent regression if a future refactor serializes the JPA entity directly.
- Happy path: `POST /auth/refresh` with a valid refresh cookie returns a new access token whose claims match the same user/org.
- Edge case: login with a username differing only in case ("NailArt" vs "nailart") succeeds (citext invariant).
- Error path: login with wrong password returns 401 with `Bad credentials` body (preserved from existing behavior).
- Error path: `/users/me` without a token returns 401.
- Error path: refresh with a revoked refresh-token cookie returns 401 and prompts the SPA to clear and redirect (existing axios interceptor logic).
- Error path: `POST /auth/register` returns 404 (route deleted; not 405, not 200).
- Error path: `GET /users/` returns 404 (route deleted; not 405, not 200). Closes the cross-tenant user-list leak before salon 2 onboards.
- Integration: full login → `/me` → logout → refresh-with-old-cookie returns 401 (revocation verified end-to-end).
- Script (bootstrap_organization_owner.py): happy path — given an existing org and a new username, the row is inserted, the bcrypt hash verifies against the input password, and the `organization_users` join row exists with role `owner`.
- Script (bootstrap_organization_owner.py): error path — second invocation with the same username exits non-zero with a clear message ("username already exists"); no partial state written.
- Script (bootstrap_organization_owner.py): error path — invocation referencing a non-existent org name exits non-zero with a clear message; no rows written.
- Frontend: `useMe()` hook returns `{ data: { user, organization }, isLoading, error }`; after `login()` + `prefetchQuery`, a subsequent component render sees the populated org timezone with no intermediate `undefined` state.
- Frontend: `<RequireMe>` boundary renders `<CircularLoading />` while `useMe()` is loading and redirects to `/Login` on error; children only render when `data` is defined.

**Verification:**
- All auth tests green against Testcontainers Postgres.
- `cd client && npm run build` passes with the new `useMe()` hook and Login page change.
- Manual: log in via the dev app; `/users/me` response in DevTools shows the right org and timezone.

---

- U3. **Multi-tenancy enforcement: `@TenantId` application-layer**

**Goal:** Enforce `organization_id` scoping via Hibernate 6 `@TenantId`. Every tenant-scoped entity carries the discriminator; the `CurrentTenantIdentifierResolver` reads from a `TenantContext` ThreadLocal populated by a web filter (HTTP requests) or by `TenantContext.runAs` (scheduled jobs). No RLS, no role split, no V2 migration, no DataSource proxy — the deepening pass's dual-layer expansion was reversed in document review as scope creep for solo dev + 1-2 tenants (see Key Technical Decisions and Alternative Approaches Considered).

**Requirements:** R6, AE4

**Dependencies:** U2

**Files:**
- Create: `api/src/main/java/com/nail_art/appointment_book/multitenancy/TenantContext.java` — `ThreadLocal<UUID>` holder with `set(UUID)`, `get()`, `clear()`, and a `runAs(UUID, Runnable/Supplier)` helper that sets+restores. **`runAs` MUST capture the prior value on entry and restore it (not `clear()`) on exit, inside a `try { ... } finally { ... }` block.** Sketch: `UUID prior = TenantContext.get(); TenantContext.set(orgId); try { return body.get(); } finally { if (prior == null) TenantContext.clear(); else TenantContext.set(prior); }`. The `clear()`-only variant breaks nested calls — an inner `runAs` returns and wipes the outer's context, after which the outer body's subsequent queries silently match zero rows under the sentinel UUID. For top-level entry points (web filter, scheduled job tick) the prior value is null and restore is equivalent to clear, so this also addresses the thread-pool-reuse concern: an uncaught exception inside `runAs` cannot leave stale tenant context in the ThreadLocal across scheduled-job ticks.
- Create: `api/src/main/java/com/nail_art/appointment_book/multitenancy/CurrentTenantResolver.java` — implements Hibernate `CurrentTenantIdentifierResolver<UUID>`; reads from `TenantContext`. **Returns a sentinel UUID (`UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")`, the RFC 9562 Max UUID — a value `gen_random_uuid()` cannot produce because v4's fixed version/variant bits forbid all-ones) when `TenantContext` is unset, NOT null** — Hibernate 6's `CurrentTenantIdentifierResolver<T>` contract requires non-null returns; returning null produces a `HibernateException("Resolved tenant identifier should be non-null")` on most 6.x versions, or silently disables the discriminator predicate on tolerant versions (the opposite of what we want). All-ones rather than all-zeros (`new UUID(0L, 0L)`) because all-zeros is a common test-fixture default, and a fixture that happens to use the sentinel as a real org id would silently convert "unset tenant" from "matches no rows" into "matches the fixture org's rows" — exactly the bypass the design prevents. The sentinel is exported from a single source-of-truth constant; downstream test fixtures must never use this value as a real organization id. The sentinel approach is safe because (a) anonymous code paths (`/auth/login`, `/auth/refresh`) only touch `User`, `OrganizationUser`, and `RefreshToken` — bootstrap entities deliberately without `@TenantId`, so the discriminator never engages for them anyway; (b) if some unforeseen code path queries an `@TenantId` entity with an unset `TenantContext`, the sentinel UUID matches no rows. The Phase 1 test at the structural-test list ("anonymous request to `/auth/login` succeeds with `TenantContext` unset") becomes a load-bearing exit gate, not an assumption.
- Create: `api/src/main/java/com/nail_art/appointment_book/configs/TenantContextWebFilter.java` — Spring `OncePerRequestFilter` that reads `organizationId` from the authenticated principal (set by `JwtAuthenticationFilter`) and populates `TenantContext` for the duration of the request; clears in `finally`. Fires AFTER `JwtAuthenticationFilter` in the filter chain. For anonymous `/auth/**` paths the principal is null; the filter sets no context. `TenantContext.get()` returns null, which `CurrentTenantResolver` converts to the sentinel UUID — anonymous code paths only touch non-`@TenantId` entities, so the sentinel never engages the discriminator predicate in practice. Do NOT return null from `CurrentTenantResolver` itself (see its spec above).
- Modify: `api/src/main/java/com/nail_art/appointment_book/configs/SecurityConfiguration.java` — register `TenantContextWebFilter` after `JwtAuthenticationFilter`.
- Modify: `api/src/main/resources/application.properties` — wire `hibernate.multiTenancy=DISCRIMINATOR` and the `CurrentTenantResolver` bean (Hibernate 6 standard pattern for discriminator-based tenancy).
- Test: `api/src/test/java/com/nail_art/appointment_book/multitenancy/TenantContextIntegrationTest.java` — structural tests using the User/Organization entities from U2 (full cross-domain AE4 test deferred to Phase 2 exit gate).

**Approach:**
- Per-entity application: every domain `@Entity` introduced in U4–U7 (`Employee`, `Service`, `Client`, `Appointment`, `AppointmentServiceLink`) carries `@TenantId @Column(name = "organization_id")` on the `organizationId UUID` field. Hibernate auto-populates on insert from `CurrentTenantIdentifierResolver` and auto-filters on every load — derived queries, `EntityManager.find()`, criteria queries all get the discriminator predicate added by Hibernate's query rewriter.
- **Bootstrap entities (`User`, `OrganizationUser`, `RefreshToken`) deliberately do NOT carry `@TenantId`.** Login resolves a user by username (anonymous path, no tenant context); the application code then looks up the user's primary `OrganizationUser` row to learn the `organizationId` and stamp it on the JWT. These entities are explicitly outside the tenant-discriminator system. Risk: a future contributor could add a query against `User` that leaks cross-tenant; that risk is accepted (solo dev, code review is the control). See Risks.
- **Why no RLS:** the deepening pass added RLS as a "structural backstop" against native queries, future contributors bypassing the ORM, and scheduled-job mistakes. Reversed during document review because (a) the realistic bugs (`@TenantId` wiring + missed `runAs` in scheduled jobs) are not caught by RLS in a useful way — RLS just changes "wrong tenant's data" into "zero rows"; (b) the carve-outs (`organizations` exempted, `users` permissive policy, login bootstrap, migration script owner-bypass, scheduled-job `SET LOCAL`) erode the structural guarantee faster than they add correctness; (c) `@Query(nativeQuery=true)` is the strongest argument for RLS but doesn't exist in the repo today and is easy to catch in code review when it appears.
- Tenant context for the migration script: no change — `migrate_mongo_to_postgres.py` connects with whatever Postgres credentials are configured, no RLS to worry about.
- Tenant context for `@Scheduled` jobs (SMS reminders): scheduled jobs have no inherent tenant. They MUST explicitly wrap their body in `TenantContext.runAs(orgId, ...)` so `CurrentTenantIdentifierResolver` returns the right org for the `@TenantId` discriminator on subsequent queries. Loop of size 1 today (one tenant); grows to N when the second salon onboards. See U7 SmsService.

**Patterns to follow:**
- Existing `JwtAuthenticationFilter` (`configs/`) for principal access patterns; `TenantContextWebFilter` registers immediately after it in the chain.
- Hibernate 6 user guide, "Discriminator-based multi-tenancy" section — `hibernate.multiTenancy=DISCRIMINATOR`, `@TenantId` on entities, `CurrentTenantIdentifierResolver<UUID>` bean.

**Test scenarios:** (bootstrap + sentinel-UUID at U3; full `@TenantId` discriminator coverage on real entities deferred to U4 onward; AE4 cross-domain at end of Phase 2)
- Structural: `CurrentTenantResolver` unit test — with `TenantContext.get() == null`, returns the sentinel UUID `ffffffff-...`, not null. With `TenantContext.set(orgA)`, returns `orgA`. Save-and-restore semantics of `TenantContext.runAs` verified via nested invocation: `runAs(orgA, () -> runAs(orgB, () -> assertEquals(orgB, get()))); assertEquals(orgA, get());`.
- Structural: anonymous request to `/auth/login` succeeds with `TenantContext` unset; the User/OrganizationUser lookup runs without a tenant discriminator (these entities are not `@TenantId`-annotated).
- Edge case: a request with a valid JWT but `organizationId` claim referring to a non-existent organization sets the context to a UUID that won't match any row — every `@TenantId`-discriminated query returns empty (verified once `Employee` exists in U4).
- **`@TenantId` discriminator behavior on real entities** (insert auto-populate, select auto-filter, `EntityManager.find()`, criteria queries) is verified in U4 against the real `Employee` entity in its `EmployeeRepositoryIntegrationTest`. Composite-key (`@EmbeddedId`) discriminator behavior is verified in U7 against the real `AppointmentServiceLink` entity in its `AppointmentRepositoryIntegrationTest`. Rationale: with `ddl-auto=none` + Flyway managing schema, a test-only stub `@Entity` in `src/test/java` would have no backing table; either a separate test-only Flyway migration or an `ddl-auto` override would be required. Verifying against the real entities in their own units avoids that complexity without delaying discovery (U4 ships immediately after U3).
- **AE4 cross-domain isolation test is deferred** to `CrossOrgIsolationIntegrationTest` at the end of Phase 2 (after U7), once all domain entities exist. That test seeds two orgs with rows in every domain, authenticates as user A, queries every domain endpoint, and asserts zero rows from org B leak — the full structural verification of the `@TenantId` discriminator system end-to-end.

**Verification:**
- `TenantContextIntegrationTest` green (bootstrap + sentinel-UUID + save-and-restore scenarios).
- Manual: `@TenantId` is applied to every new `@Entity` in U4–U7 (verified at each unit's PR / commit).
- U4 gate: `EmployeeRepositoryIntegrationTest` exercises full `@TenantId` discriminator behavior (insert auto-populate, derived-query filter, `EntityManager.find()`, criteria) on the real `Employee` entity — the structural mechanism is proven there.
- U7 gate: `AppointmentRepositoryIntegrationTest` exercises the composite-key (`@EmbeddedId`) discriminator behavior on real `AppointmentServiceLink` rows — any discriminator-on-`@EmbeddedId` Hibernate quirk surfaces here, one unit before Phase 2 closes.
- Phase 2 gate (end of U7): `CrossOrgIsolationIntegrationTest` seeds two orgs and confirms `@TenantId` blocks every cross-tenant query path (read + write — see U7 test scenarios).

---

### Phase 2: Domain vertical slices

- U4. **Employees vertical slice**

**Goal:** Replace Mongo-backed `Employee` end-to-end. JPA entity, JPA repo, service refactor, controller refactor (UUID path params), frontend EmployeesPage updates, tests.

**Requirements:** R4, R6, R9, R15

**Dependencies:** U2, U3

**Files:**
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/Employee.java` (`@Entity`, `UUID id`, `organizationId UUID` annotated `@TenantId @Column(name="organization_id")` so Hibernate auto-populates on insert and auto-filters on load)
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/EmployeeRepository.java` → `JpaRepository<Employee, UUID>`; preserve `findByNameContainingIgnoreCase` semantics
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/EmployeeService.java` (UUIDs throughout; queries unchanged in shape thanks to the filter)
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/EmployeeController.java` (`@PathVariable UUID id`)
- Modify: `client/src/api/employees/employees.ts` (and `index.ts`) — change `id: number` → `id: string`
- Modify: `client/src/EmployeesPage/*` — type changes, no behavior change
- Test: `api/src/test/java/com/nail_art/appointment_book/repositories/EmployeeRepositoryIntegrationTest.java` (new)
- Test: `api/src/test/java/com/nail_art/appointment_book/services/EmployeeServiceTest.java` (rewrite)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/EmployeeControllerIntegrationTest.java` (new — MockMvc)

**Approach:**
- The `Employee` Mongo `@Document` becomes a JPA `@Entity` in place, same filename. Old `id: long` field replaced by `id: UUID`.
- `EmployeeRepository` derived queries become Spring Data JPA `Containing` matchers — same naming convention, different backing store.
- `EmployeeController` swaps `@PathVariable int id` to `@PathVariable UUID id`. Spring converts UUID strings on path automatically.
- Frontend changes are purely type-level: every place that holds an `employee.id` becomes a `string`.

**Patterns to follow:**
- Existing search behavior: `containsIgnoreCase` on name (`docs/reference/lessons.md`).
- Existing pagination behavior on list endpoint (preserve `Pageable` semantics if present).
- `markReminderSent` lesson does not apply to Employees, but reaffirms: don't route admin-only fields through the user-facing edit path.

**Test scenarios:**
- Repo, happy path: `save(employee)` then `findById(uuid)` returns it.
- Repo, happy path: `findByNameContainingIgnoreCase("ann")` matches "Anna" and "Joanne".
- Repo, edge case: pagination respects `Pageable` size limits.
- Service, happy path: `createEmployee(dto)` persists with the org from the authenticated principal (verified by filter).
- Service, error path: editing an employee with an ID not in the user's org returns empty (filter hides it).
- Controller, happy path: `POST /employees/create` with valid body returns 201 + the created employee with a UUID id.
- Controller, happy path: `GET /employees/name/ann` returns matching rows scoped to the caller's org.
- Controller, error path: `POST /employees/create` with empty name returns 400 with `{ name: <msg> }` (preserved from existing BindingResult mapping).
- Controller, error path: `DELETE /employees/delete` for an ID belonging to another org returns 404.
- Integration: end-to-end create → list → edit → delete via MockMvc returns expected statuses and shapes.

**Verification:**
- All Employee tests green.
- `EmployeesPage` builds with `cd client && npm run build`.
- Manual: log in via dev app, navigate to Employees page, create/edit/delete works.

---

- U5. **Services vertical slice**

**Goal:** Same shape as U4 for the `Service` domain, plus the new `is_unavailability_marker` column that lets the frontend specials-color "unavailable" appointments durably (independent of the service's display name).

**Requirements:** R4, R6, R9, R15

**Dependencies:** U2, U3

**Files:**
- Create: `api/src/main/resources/db/migration/V3__add_unavailability_marker.sql` — pure DDL: `ALTER TABLE services ADD COLUMN is_unavailability_marker BOOLEAN NOT NULL DEFAULT FALSE;` + `CREATE UNIQUE INDEX services_one_unavailability_marker_per_org ON services (organization_id) WHERE is_unavailability_marker;`. **No backfill UPDATE** — Flyway migrations must be data-agnostic to stay portable across CI Testcontainers, fresh dev stacks, salon-1 cutover, and salon-2 onboarding. The cutover-day marker assignment happens inside `migration/migrate_mongo_to_postgres.py` via the `--unavailability-service-mongo-id` arg (see migration script change below); the deferred `scripts/create_organization.py` will set the marker for each new org it creates.
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/Service.java` (`@Entity`, UUID id, `organizationId` annotated `@TenantId @Column(name="organization_id")`, new `Boolean isUnavailabilityMarker` field mapped to `is_unavailability_marker`)
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/ServiceRepository.java` → `JpaRepository<Service, UUID>`; `findByNameContainingIgnoreCase`
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/ServiceService.java`
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/ServiceController.java` — response DTO/entity exposes `isUnavailabilityMarker` (read-only from API; the flag is set by seed scripts, not via the edit endpoint, to prevent owners accidentally toggling it off)
- Modify: `migration/migrate_mongo_to_postgres.py` — add `--unavailability-service-mongo-id` CLI arg (default `None`). When set, after inserting all services, set `is_unavailability_marker = TRUE` on the service whose original Mongo id matches the arg. Operator passes `--unavailability-service-mongo-id 3` on cutover (or whichever Mongo id holds today's "unavailable" service). Without the arg, the cutover proceeds without flagging anything — the operator can manually `UPDATE services` post-cutover, but the runbook (U12) instructs them to pass the arg.
- Create: `scripts/create_organization.py` — psycopg CLI taking `--name --timezone --business-phone` (and optionally `--sms-reminders-enabled`). Performs three inserts in one transaction: (1) row in `organizations` (id auto-generated UUID), (2) row in `organization_settings` keyed by that org id with the requested SMS flag, (3) row in `services` for the per-tenant "Unavailable" service with `is_unavailability_marker = true`. Prints the new org's UUID on success so the operator can pass it to `bootstrap_organization_owner.py` for the first owner. Used for salon-2 onboarding without waiting for a separate follow-up branch. Reuses the migration script's `psycopg` deps. Depends on U5's V3 migration (the `is_unavailability_marker` column must exist) — therefore lives in U5, not earlier.
- Modify: `client/src/api/services/services.ts` — add `isUnavailabilityMarker: boolean` to the Service type
- Create: `client/src/utils/colors.ts` — single source of truth for cross-component color constants. Export `UNAVAILABILITY_BG = "#000000"` (matches today's `MobileCalendar.tsx:253` hardcoded value; preserved verbatim so the owner's visual recognition is unchanged) plus `UNAVAILABILITY_FG = "#FFFFFF"` for foreground contrast. Both consumed by U5's `<Chip>` and by U8's calendar special-color paths (MobileCalendar, TimeSlots, Appointment). NOT placed on `theme.palette` because the value is a structural-marker color, not a brand color — putting it on the palette implies it participates in theme switching, which it doesn't.
- Modify: `client/src/ServicesPage/*` — display a small MUI `<Chip>` with label "Unavailable" next to the service name on the marker row, styled with `sx={{ bgcolor: UNAVAILABILITY_BG, color: UNAVAILABILITY_FG }}` imported from `client/src/utils/colors.ts`. The chip signals "this is the structural unavailability service; renaming it won't change the special-color rule." The marker flag is NOT user-editable from this UI.
- Test: `api/src/test/java/com/nail_art/appointment_book/repositories/ServiceRepositoryIntegrationTest.java` (new)
- Test: `api/src/test/java/com/nail_art/appointment_book/services/ServiceServiceTest.java` (rewrite)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/ServiceControllerIntegrationTest.java` (new)
- Test: `scripts/test_create_organization.py` (pytest + psycopg against dev-stack Postgres) — happy path: invocation creates exactly one row in `organizations`, one in `organization_settings`, and one in `services` with `is_unavailability_marker = true`, all linked by org id, in a single transaction. Error path: duplicate org name surfaces the unique-constraint error cleanly; no partial state.

**Approach:**
- Same template as U4. Schema enforces `UNIQUE (organization_id, lower(name))` so duplicate-name protection comes for free; map `DataIntegrityViolationException` to 409 in `GlobalExceptionHandler` if not already handled by U6's similar change.
- The `is_unavailability_marker` flag is a structural property: set once at seed time, not editable through the user-facing edit endpoint. Owner can rename the service freely; the flag stays. The partial unique constraint guarantees exactly one marker row per org (or zero, if the org doesn't use the feature) — preventing the "two unavailable services" footgun.
- Backend exposes the flag as read-only in the API response (the Services edit endpoint ignores any inbound `isUnavailabilityMarker` field). Only `migration/migrate_mongo_to_postgres.py` and future `scripts/create_organization.py` (deferred) set it.

**Patterns to follow:**
- U4 — mirror file-for-file.
- `ContainingIgnoreCase` for the search method.

**Test scenarios:**
- Mirror U4's scenario list, adapted for Services.
- Additional: attempting to create a service whose name only differs in case from an existing service in the same org returns 409 (unique-constraint violation mapped).
- Migration: with `--unavailability-service-mongo-id 3`, the row corresponding to Mongo service id 3 has `is_unavailability_marker = TRUE` after migration; all other rows have `FALSE`.
- Migration: without the arg, no rows are flagged (and a follow-up `UPDATE services SET is_unavailability_marker = TRUE WHERE id = ?` works without violating the partial unique constraint, since zero is allowed).
- Schema constraint: attempting to insert two `is_unavailability_marker = TRUE` rows in the same org via raw SQL fails on the partial unique constraint.
- Schema constraint: two different orgs can each have their own marker row (uniqueness is per-org).
- API: `GET /services` includes the `isUnavailabilityMarker` boolean for each row.
- API: `PUT /services/edit` with `isUnavailabilityMarker: false` for the marker row leaves the flag unchanged (write-protected from the public API surface). Renaming the marker row via the same endpoint succeeds; the flag stays true.

**Verification:**
- All Service tests green; ServicesPage builds.
- V3 migration applies cleanly against the dev-stack Postgres and against the cutover dry-run target.

---

- U6. **Clients vertical slice**

**Goal:** Same shape as U4/U5 for `Client`. Adds unique-phone-per-org constraint mapping and preserves the `2000` pagination cap.

**Requirements:** R4, R6, R9, R15

**Dependencies:** U2, U3

**Files:**
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/Client.java` (`@Entity`, UUID id, `organizationId` annotated `@TenantId @Column(name="organization_id")`)
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/ClientRepository.java` → JPA; preserve regex/contains search semantics on `name` and `phoneNumber`
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/ClientService.java` (delete the boot-time index verification that lived in `MongoConfig`; the schema enforces uniqueness now)
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/ClientController.java`
- Modify: `api/src/main/java/com/nail_art/appointment_book/exceptions/GlobalExceptionHandler.java` (replace `DuplicateKeyException → 409` mapping with `DataIntegrityViolationException → 409`; preserve user-visible behavior; conflict response body shape is `{ description: "<message>" }` to match the existing frontend `err.response.data.description` reader)
- **Delete** (entire file, no empty shell): `api/src/main/java/com/nail_art/appointment_book/configs/MongoConfig.java` — its sole responsibility was Mongo index verification, which is now schema-enforced. U10 no longer touches this file.
- Modify: `client/src/api/clients/clients.ts`, `client/src/ClientsPage/*`
- Test: `api/src/test/java/com/nail_art/appointment_book/repositories/ClientRepositoryIntegrationTest.java` (new)
- Test: `api/src/test/java/com/nail_art/appointment_book/services/ClientServiceTest.java` (rewrite)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/ClientControllerIntegrationTest.java` (new)

**Approach:**
- Phone uniqueness: schema constraint `UNIQUE (organization_id, phone_number) WHERE phone_number <> ''`. Empty phone is allowed and duplicates allowed when phone is empty (anonymous clients). Service-level pre-check still nice for clean 409 messaging, but the constraint is the authoritative line.
- Search: name and phone are partial/case-insensitive. Map Mongo regex matchers to `findByNameContainingIgnoreCaseOrPhoneNumberContaining(...)` derived query or a `@Query` with `ILIKE`.
- Pagination cap `2000` on `/clients` — preserve. Frontend dropdown cap must still match (lesson from `docs/reference/lessons.md`).
- `MongoConfig` is emptied here (its only responsibility was Mongo index verification); deletion of the file happens in U10 as part of decom.

**Patterns to follow:**
- U4 base template.
- `docs/reference/lessons.md`: "Don't bypass `ClientService.createClient`. When linking appointments to clients, look up by phone number, not by trusting the inbound `clientId`." Preserve this behavior in U7.

**Test scenarios:**
- Mirror U4 base scenarios for Clients.
- Edge case: create two clients with empty phone numbers — both succeed (partial unique index excludes empty).
- Error path: create a client whose phone number duplicates an existing client (same org) returns 409.
- Error path: create a client whose phone matches another org's client succeeds (org-scoped uniqueness).
- Edge case: list endpoint with `size=2000` returns up to 2000 rows; `size=2001` is capped at 2000 (preserve existing cap semantics — implemented via controller-side `PageRequest.of(page, Math.min(size, 2000), Sort.by("id").descending())` mirroring the current `ClientController` line 57).
- Integration: phone search for `"555-12"` against a row with `phone_number="(555) 123-4567"` returns NO MATCH. This is the **current production behavior** — `ClientService.searchClients` uses `Criteria.where("phoneNumber").regex(input, "i")` with no formatting normalization. The dash inside `"555-12"` does not appear inside `"(555) 123-4567"` as a literal substring, so neither Mongo regex nor JPA `findByPhoneNumberContaining` matches. Preserves parity, not "regex weirdness."
- Integration: phone search for `"5551"` against `phone_number="(555) 123-4567"` ALSO returns NO MATCH (no literal "5551" substring after the stripped digits; the stored value is the formatted form). Owner searches must use a substring that actually appears in the stored format.
- Integration: phone search for `"555) 12"` against `phone_number="(555) 123-4567"` returns a MATCH (literal substring present). Confirms the JPA `Containing` behavior matches the prior regex behavior for the realistic search inputs.
- Edge case: name search for `"a.n"` against `name="Anna"` matches under JPA `LIKE` only if the literal `a.n` appears (no match here). Under the prior Mongo regex, `.` was a metachar matching any single char, so `"a.n"` would match `"Anna"` (the `nn` between `a` and `a` contained the `n`, plus regex anchoring). This is a tiny behavior delta — SQL `LIKE` is more literal. Acceptable for a salon owner typing real names; no service-layer normalization needed.

**Verification:**
- All Client tests green; ClientsPage builds; pagination behavior matches `docs/reference/lessons.md` lesson.

---

- U7. **Appointments backend**

**Goal:** Port appointment persistence and business logic — entity + junction table, repository with org-scoped queries, service with interval-based conflict detection, controller with UUID path params and new field shape, SMS service updated for timezone-aware tomorrow query. Preserve `markReminderSent` as a non-conflict-checking path.

**Requirements:** R4, R5, R6, R9, R15, R16, AE2, AE3, F3

**Dependencies:** U2, U3, U4, U5, U6 (Appointments references Employee, Service, and Client; those must be JPA-ready)

**Files:**
- Modify: `api/src/main/java/com/nail_art/appointment_book/entities/Appointment.java` (`@Entity`, UUID id, `organizationId` annotated `@TenantId @Column(name="organization_id")`, `OffsetDateTime startsAt/endsAt`, `archived_at`, `reminder_sent_at`)
- Create: `api/src/main/java/com/nail_art/appointment_book/entities/AppointmentServiceLink.java` (junction entity for `appointment_services`; composite PK `(appointment_id, service_id)`; carries `organization_id` per schema)
- Modify: `api/src/main/java/com/nail_art/appointment_book/repositories/AppointmentRepository.java` → JPA; `findByStartsAtBetween(OffsetDateTime, OffsetDateTime)` for date-scoped queries; `findByPhoneNumberContaining` for search; `findByEmployeeIdAndStartsAtBetween` for conflict detection
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/AppointmentService.java` (rewrite conflict detection to interval comparison; `getAppointmentsByDate(LocalDate)` accepts a date in the org's timezone and computes the `[startOfDay, endOfDay)` range as `OffsetDateTime` for query; `getAppointmentsForTomorrow()` becomes timezone-aware; `markReminderSent` stays as a dedicated update path that does NOT route through edit)
- Modify: `api/src/main/java/com/nail_art/appointment_book/controllers/AppointmentController.java` (`@PathVariable UUID id`; request body uses new field shape `startsAt`/`endsAt` instead of `date`/`startTime`/`endTime`; `GET /appointments/date/{date}` still accepts `YYYY-MM-DD` and converts to range internally)
- Modify: `api/src/main/java/com/nail_art/appointment_book/services/SmsService.java` — refactor `sendReminders()` to the **iterate-orgs-loop pattern** (with a loop of size 1 today). Concrete shape: (a) query for orgs with SMS reminders enabled. **`sms_reminders_enabled` lives on `organization_settings` (V1 schema line 32), NOT on `organizations`** — implement the lookup as either a `@Query` on `OrganizationRepository` that JOINs `organization_settings`, or a method on `OrganizationSettingsRepository` that returns the associated org ids. Pick at implementation time once the deferred `OrganizationSettings`-as-entity-vs-`@Embeddable` decision is made. `Organization` is a bootstrap entity (no `@TenantId`) so this lookup is unscoped by design; (b) for each org, wrap the body in `TenantContext.runAs(orgId, () -> { ... })`; (c) inside the runAs, query `getAppointmentsForTomorrow()` which now sees the right tenant via Hibernate's `CurrentTenantIdentifierResolver` and uses the org's stored timezone for "tomorrow"; (d) **for each appointment, the Twilio dispatch happens OUTSIDE any DB transaction (no connection held during Twilio I/O and retry backoff sleeps), and `markReminderSent` opens its own small transaction on success only** — preserves today's failure semantics (a Twilio failure on one appointment doesn't block others; no connection idled during retry sleeps); (e) per-org try/catch isolation so one org's failure doesn't block others. Twilio credentials remain env-var-sourced this branch (per-tenant Twilio creds storage is in a follow-up branch per `docs/brainstorms/2026-05-24-salon-2-prep-notes.md`). Cron schedule unchanged. `markReminderSent` stays a dedicated non-conflict-checking method. **Salon-2 onboarding is gated by the operator (the developer) on the per-tenant Twilio follow-up landing first** — there is no runtime gate in this file; if a second org row appears with `sms_reminders_enabled = TRUE` before per-tenant Twilio storage exists, it will dispatch from salon-1's number. That sequencing is enforced by the operator's workflow, not by code, per the user's explicit constraint that no second org row will be created until per-tenant Twilio storage lands.
- Test: `api/src/test/java/com/nail_art/appointment_book/repositories/AppointmentRepositoryIntegrationTest.java` (new)
- Test: `api/src/test/java/com/nail_art/appointment_book/services/AppointmentServiceTest.java` (rewrite)
- Test: `api/src/test/java/com/nail_art/appointment_book/controllers/AppointmentControllerIntegrationTest.java` (new)
- Test: `api/src/test/java/com/nail_art/appointment_book/services/SmsServiceTest.java` (update; preserve existing scenarios where applicable)

**Approach:**
- The schema's `appointment_services` junction has composite PK `(appointment_id, service_id)` and a `organization_id` column participating in composite FKs back to both `appointments (id, organization_id)` and `services (id, organization_id)`. Model as an **explicit `@Entity` with `@EmbeddedId`** (composite key on `appointmentId`, `serviceId`) plus `organizationId` as a non-key column populated from the parent on insert. Hibernate `@ManyToMany` with `@JoinTable` cannot populate a non-key column on the junction, so the composite-FK enforcement makes the explicit-entity path mandatory. Cascade-on-delete for the appointment-side relationship relies on the schema's declared `ON DELETE CASCADE`.
- Conflict detection runs only on create/edit (preserve existing surface area; `docs/reference/lessons.md` lesson). New rule: see High-Level Technical Design above. Exclude self by `id` when editing.
- `getAppointmentsForTomorrow` needs salon-tz-aware "tomorrow." Read the org's timezone (from the seed/migration row), compute `LocalDate.now(salonZone).plusDays(1)`, build `[startOfDay, endOfDay)` in that zone, convert to `OffsetDateTime` for the query.
- `getAppointmentsByDate` receives `YYYY-MM-DD` from the controller path. Same `LocalDate` → range conversion using the org's timezone (NOT the server's; NOT the request's locale).
- `markReminderSent` does a targeted UPDATE that sets `reminder_sent_at` and nothing else. No conflict check, no edit-path side effects. Preserve the lesson.
- `archived_at` is a soft-delete column on the single `appointments` table — there is no separate `archived_appointments` table in V1 schema. Archive query in the cron sets `archived_at = now()`; list endpoints filter `archived_at IS NULL` by default.
- `phoneNumber` on `Appointment` stays as a denormalized text column for unlinked appointments. When linking by phone to an existing client, preserve the `docs/reference/lessons.md` "look up by phone, not trust clientId" behavior in `editAppointment`.
- Customer name (`customer_name`) is NOT NULL — preserve form validation that surfaces this.

**Patterns to follow:**
- Existing `AppointmentService` conflict-check method shape — same public API surface, internals swap to interval comparison.
- `SmsService`'s short-circuit on Twilio `21610` and retry-on-`5xx`/`429` — preserve verbatim.
- `AGENTS.md`: "Don't talk to Twilio outside `SmsService`. Don't route admin-only updates through `editAppointment`."

**Test scenarios:**
- Repo, happy path: `findByStartsAtBetween` returns appointments whose `startsAt` falls in the inclusive-exclusive range.
- Repo, integration: an appointment with `archived_at IS NOT NULL` is excluded from the default list query.
- Service, happy path: `createAppointment` with a new client name and phone creates the client and the appointment in one logical flow (preserve existing behavior).
- Service, happy path: `editAppointment` with the same `id` and a new time slot succeeds (self-exclusion in conflict check).
- **Covers AE2.** Service, error path: creating an appointment for employee X at 10:30–11:30 when X already has 10:00–11:00 returns conflict; creating at 11:00–12:00 succeeds.
- Service, error path: two appointments at the same start/end time for the same employee are conflicts (`existing.starts_at < new.ends_at AND existing.ends_at > new.starts_at` is true when both intervals are equal).
- Service, error path: `endsAt <= startsAt` is prevented by the CHECK constraint, surfaces as `DataIntegrityViolationException`, maps to 400 with a `endTime` field message.
- Service, integration: `markReminderSent` updates `reminder_sent_at` without re-running conflict checks — verified by setting up a scenario where edit-path would fail (e.g., adjacent conflicting appointment) and confirming `markReminderSent` still succeeds.
- Service, integration: `editAppointment` for an appointment with no `clientId` but a phone matching an existing client links the client (preserve lesson).
- **Covers AE3.** Service, integration: with the test clock set to 23:30 America/New_York (which is 03:30 next-day UTC), `getAppointmentsByDate(today-in-ET)` returns today's appointments — not next-day's.
- Service, edge case: `getAppointmentsForTomorrow` at noon ET returns appointments whose `startsAt` falls on the next ET calendar day, regardless of UTC.
- Controller, happy path: `POST /appointments/create` with `{startsAt, endsAt, ...}` returns 201 + the created appointment with UUID id and ISO `startsAt`/`endsAt` in response.
- Controller, happy path: `GET /appointments/date/2026-05-26` returns appointments for that date in the org's tz.
- Controller, error path: validation errors return 400 with field map.
- Controller, error path: `POST /appointments/create` with a conflicting time slot returns 409 with body `{ "description": "<human-readable conflict message>" }`. Body shape MUST match what `AppointmentModal.tsx` reads (`err.response.data.description`), otherwise the modal silently displays its fallback text and the owner cannot see which slot conflicted.
- Controller, integration: `GET /appointments/search/<phone>` returns partial matches scoped to caller's org.
- **Cross-org write attacks** (covers Phase 2 exit gate write-path requirements): (a) authenticated as user A, `PUT /appointments/<org-B-appointment-uuid>` returns 404; the org-B row in DB is unchanged. (b) authenticated as user A, `POST /appointments/create` with `employeeId` set to an org-B employee UUID fails (the V1 composite FK `appointments(employee_id, organization_id) → employees(id, organization_id)` at schema lines 108-109 makes the row structurally unconstructable with mismatched org). (c) `POST /appointments/create` with a phone string that matches an org-B client creates a new org-A client (the existing "look up by phone" lesson runs `@TenantId`-scoped, returns null, falls through to create). (d) `DELETE /appointments/delete` for an org-B appointment uuid returns 404; org-B row unchanged.

**Verification:**
- All Appointment tests green; SMS tests green; manual: existing SMS scheduled job dry-runs in dev (set system clock or temporary cron override) and dispatches against the migrated dataset without error.

---

- U8. **Appointments frontend**

**Goal:** Update calendar, search, modal/form, and detail views to the new API shape; introduce dayjs timezone discipline via a shared helper module; consume `orgTz` from `useMe()`.

**Requirements:** R5, R9, R10, R11, R16, F3, AE1

**Dependencies:** U2 (useMe hook), U7 (backend contract)

**Files:**
- Modify: `client/package.json` — verify `dayjs/plugin/utc` and `dayjs/plugin/timezone` are tree-shakeable from the existing install. (Vitest already added in U1.)
- Create: `client/src/utils/datetime.ts` (helper module: `formatTime(iso, orgTz)`, `formatDate(iso, orgTz)`, `formatDateTime(iso, orgTz)`, `toIsoFromSalonInput(date, time, orgTz)`, `minutesFromMidnight(iso, orgTz)`. dayjs plugin setup happens at module load; consumers import from this module, not raw dayjs. **Helpers throw on undefined `orgTz`** — fail-fast, never silently fall back to browser tz. The `<RequireMe>` boundary in U2 guarantees orgTz is defined before any consumer renders.)
- Modify: `client/src/types/Appointment.ts` — `id: string`, `services: string[]`, replace `date`/`startTime`/`endTime` with `startsAt: string` / `endsAt: string`. Phone, customerName, employeeId (string), reminderSentAt (string|null), archivedAt (string|null), showedUp (boolean) preserved.
- Modify: `client/src/api/appointments/appointments.ts` — request/response types: `startsAt` / `endsAt` ISO strings; ids become strings.
- Modify: `client/src/AppointmentsPage/Calendar/Calendar.tsx` — date math uses helper. Specifically: `startDate` state initializer reads `localStorage.getItem("startDate")` and parses with `dayjs(stored, "YYYY-MM-DD").tz(orgTz, true)` (keepLocalTime=true preserves stored calendar date); default is `dayjs().tz(orgTz)`. Subtitle uses `startDate.tz(orgTz).format("dddd, MMMM D, YYYY")`.
- Modify: `client/src/AppointmentsPage/Calendar/components/CalendarHeader.tsx` — same. `isToday` becomes `startDate.isSame(dayjs().tz(orgTz), "day")`.
- Modify: `client/src/AppointmentsPage/Calendar/components/MobileCalendar.tsx`:
  - Line 107: `useState(dayjs())` → `useState(dayjs().tz(orgTz))`
  - Line 109 interval: `setNow(dayjs())` → `setNow(dayjs().tz(orgTz))`
  - Line 116 `isToday`: compare against `dayjs().tz(orgTz)`
  - Line 117-119 nowOffset: derive `now.hour()` / `now.minute()` from the tz-converted instance
  - Lines 244-245 appointment block math: `dayjs(app.date + app.startTime)` → `dayjs(app.startsAt).tz(orgTz)` (and same for endTime)
  - Line 402 detail formatting: `dayjs(detailApp.date + detailApp.startTime).format("h:mm A")` → `formatTime(detailApp.startsAt, orgTz)` via the helper
  - Line 253 `isServiceType3 = app.services.includes(3)`: hardcoded magic number is invalid under UUIDs. Replace with a **flag-based lookup** against the `services` list (using the `is_unavailability_marker` column added in U5): `const unavailService = services?.find(s => s.isUnavailabilityMarker); const isUnavailabilityService = unavailService ? app.services.includes(unavailService.id) : false;`. Rename the local variable from `isServiceType3` to `isUnavailabilityService` everywhere it's referenced in this file. **Preserve the existing visual treatment**: replace the inline `blockColor = "#000000"` literal with `blockColor = UNAVAILABILITY_BG` imported from `client/src/utils/colors.ts` (the constant added in U5). Opacity and border behavior unchanged. The flag-based rename is the only behavior change; the appearance must look identical to today's "Unavailable" appointments.
  - Line 101 `getServiceNames(serviceIds: number[])` → `getServiceNames(serviceIds: string[])`.
- Modify: `client/src/AppointmentsPage/Calendar/components/CustomCalendar.tsx` and the desktop calendar grid — apply the same `.tz(orgTz)` discipline; `createApp` initial state uses `startsAt` / `endsAt` produced by `toIsoFromSalonInput`, never the deprecated string fields. **Apply `isUnavailabilityService` flag-based special-color treatment on the desktop calendar too** — the owner's intent is that unavailability appointments render distinctly on BOTH mobile and desktop (per clarification 2026-05-25). NOTE: the desktop path's `Appointment.tsx` currently has no bgcolor logic (only an `isSpecial` text-color prop chain), so this is net-new rendering, not a "preserve" operation. See `Appointment.tsx` and `TimeSlots.tsx` modifications below for the exact prop-and-style wiring.
- Modify: `client/src/AppointmentsPage/Calendar/components/CustomCalendar/Appointment.tsx` — lines 19-24 use `dayjs(appointment.date + appointment.startTime)`; replace with `formatTime(appointment.startsAt, orgTz)` via the helper. Without this edit the file silently compiles and renders `NaN:NaN` for time displays post-cutover. **Add a new `bgcolor?: string` prop**, applied to the root container's `sx={{ bgcolor: bgcolor ?? <existing-default> }}`. When `isSpecial` is true AND the appointment is an unavailability appointment, the parent (`TimeSlots.tsx`) passes `bgcolor={UNAVAILABILITY_BG}` and the existing `isSpecial`-driven text-color treatment continues to apply, producing a visual that matches MobileCalendar's unavailability rendering. When `isSpecial` is true for the `showedUp` case (existing behavior), the parent passes the existing showedUp color. The two cases are routed through the same new prop. Existing `isSpecial` semantics (white text, no opacity change) preserved.
- Modify: `client/src/AppointmentsPage/Calendar/components/CustomCalendar/TimeSlots.tsx` — lines 57-66 (`isSlotOverlapped`) currently constructs `dayjs(arbitraryDate + app.startTime.split("T")[1])` and the same for endTime; rewrite to `dayjs(app.startsAt).tz(orgTz)` and `dayjs(app.endsAt).tz(orgTz)`. The split-at-T pattern discards the timezone offset and parses in browser tz, which U8's discipline forbids. Also compute `isUnavailabilityService` here using the flag-based lookup (`services?.find(s => s.isUnavailabilityMarker)`); when true, pass `bgcolor={UNAVAILABILITY_BG}` (imported from `client/src/utils/colors.ts`) AND `isSpecial={true}` down to `<Appointment />`. When `app.showedUp`, pass `bgcolor={<existing-showedUp-color>}` and `isSpecial={true}` as today. Both cases route through the new bgcolor prop added to `Appointment.tsx`.
- Modify: `client/src/AppointmentsPage/Calendar/components/*Modal*.tsx` and appointment-form components:
  - Date-picker `value=` props initialize from `dayjs(form.startsAt).tz(orgTz)` and `dayjs(form.endsAt).tz(orgTz)`, not from the deprecated string-concat pattern.
  - All `onChange` handlers convert back via `toIsoFromSalonInput(date, time, orgTz)` and write `form.startsAt` / `form.endsAt`.
  - End-time validation guard becomes `dayjs(form.endsAt).tz(orgTz).isBefore(dayjs(form.startsAt).tz(orgTz))` (replaces the now-broken `dayjs(form.date + form.startTime)` comparison).
  - Services `Select`: change `Select<number[]>` to `Select<string[]>`; remove `.map(Number)`; preserve multi-select behavior.
  - Picker `timezone` configuration: in MUI x-date-pickers v8 the `timezone` prop is on the individual picker components (`<DatePicker>`, `<TimePicker>`, `<DateTimePicker>`, `<DateField>`, `<DateCalendar>`, etc.) via the `TimezoneProps` mixin, **NOT on `<LocalizationProvider>`** (verified against installed `@mui/x-date-pickers@8.27.2`: declared in `models/timezone.d.ts`, absent from `LocalizationProvider/LocalizationProvider.d.ts`). Pass `timezone={orgTz}` to every picker instance. Specifically: the four pickers inside `AppointmentModal.tsx` (one `MobileDatePicker` + two `MobileTimePicker` in the mobile branch, two `DateTimePicker` in the desktop branch — each needs the prop individually); the `DateCalendar` instances inside the popovers of `CalendarHeader.tsx` (line 83 today), `MobileDateHeader.tsx` (line 63), and `CalendarNavigator.tsx` (line 24). A grep `git grep -nE '<(Date|Time|DateTime)(Picker|Calendar|Field)|<MobileDate|<MobileTime'` inside `client/src/` enumerates the call sites; the implementer must verify every result has `timezone={orgTz}` after the U8 pass. `orgTz` comes from `useMe()`; the `<RequireMe>` boundary guarantees it is defined before any picker renders. If any picker is mounted above `<RequireMe>` in the tree, move it inside.
  - **Picker `minTime`/`maxTime` business-hours constraints**: today's `AppointmentModal.tsx` defines `nineAM = dayjs().hour(9).minute(0).second(0)` and `ninePM = dayjs().hour(21).minute(0).second(0)` as bare dayjs instances (browser-tz). Once each picker carries `timezone={orgTz}`, MUI interprets these constraints in the picker's tz context, which means a Pacific-tz browser viewing a NY-salon picker would see the constraints shifted by the offset. Rewrite the constants to `dayjs.tz(\`${todayInSalonTz}T09:00:00\`, orgTz)` and `dayjs.tz(\`${todayInSalonTz}T21:00:00\`, orgTz)` — or add `businessHoursMin(orgTz)` / `businessHoursMax(orgTz)` helpers to `client/src/utils/datetime.ts` that throw on undefined orgTz (same fail-fast rule). Test scenario added in U8: rendering AppointmentModal in a Pacific-tz test environment with `orgTz="America/New_York"` blocks bookings before 9 AM NY time (not 9 AM Pacific time).
- Modify: `client/src/AppointmentsPage/Search/Search.tsx` — display uses helper; result fields renamed.
- Test: `client/src/utils/datetime.test.ts` (vitest)
- Test: `client/src/AppointmentsPage/Calendar/components/MobileCalendar.test.tsx` (vitest + @testing-library/react) — render with a non-ET test environment and an ET orgTz; assert appointment block at the right grid offset.

**Approach:**
- Single helper module is the chokepoint. All appointment timestamp rendering goes through it. Code review enforces no raw `dayjs(...).format(...)` on appointment fields.
- `orgTz` is always read from `useMe()` and passed to the helper. A hook wrapper (`useFormatTime`) can be added later for ergonomic call sites but isn't required.
- Form submission: receptionist picks date + time (each as separate inputs in salon-local terms). The helper converts `(date, time, orgTz)` → ISO string with the org's offset for the API.
- The helper internally calls `dayjs.extend(utc)` and `dayjs.extend(timezone)` once at module load.

**Patterns to follow:**
- `client/src/api/<resource>/<resource>.ts` shape — preserve.
- Existing MobileCalendar's "now" useState/interval pattern — preserve, but `dayjs()` becomes `dayjs().tz(orgTz)`.
- AGENTS.md "Page directories are PascalCase; shared primitives go in `client/src/components/`." — helper goes in `client/src/utils/` (not a component); follows existing convention (e.g., theme tokens).

**Test scenarios:**
- **Covers AE1.** Happy path: with `orgTz="America/New_York"`, `formatTime("2026-05-26T13:30:00Z", "America/New_York")` returns `"9:30 AM"`. Same input with `orgTz="America/Los_Angeles"` returns `"6:30 AM"` (proving the helper respects the passed tz, not the test environment).
- Happy path: `toIsoFromSalonInput("2026-05-26", "09:30", "America/New_York")` returns an ISO string parsing back to `2026-05-26T13:30:00.000Z`.
- Edge case: `minutesFromMidnight("2026-05-26T13:30:00Z", "America/New_York")` returns `570` (9*60+30).
- Edge case: `formatTime` of `"2026-05-26T00:30:00Z"` in `America/New_York` returns `"8:30 PM"` (previous day in salon tz — proves tz conversion, not naive parsing).
- Error path: `formatTime("2026-05-26T13:30:00Z", undefined)` throws an error rather than falling back to browser tz. Same for the other helpers. Ensures the `<RequireMe>` gate cannot be silently circumvented.
- Integration: with the test runner's system tz forced to Pacific, a render of MobileCalendar with `orgTz="America/New_York"` and an appointment at `13:30Z` lays out the appointment at the 9:30 AM grid position (not 6:30 AM).
- Integration: AppointmentModal opens for editing an existing appointment with `startsAt="2026-05-26T13:30:00Z"`; the date-picker initial value renders as `9:30 AM` on the salon-tz date `2026-05-26`. Changing the end time to `9:00 AM` triggers the end-time validation guard and the submit button stays disabled.
- Round-trip: form picks `2026-05-26 09:30`; helper produces ISO; helper formats that ISO back; result is `"9:30 AM"` on the same date.

**Verification:**
- `client/src/utils/datetime.test.ts` green.
- `cd client && npm run build` passes (AGENTS.md gate).
- `cd client && npx tsc -b --noEmit` and `npm run lint` pass.
- Manual: load the calendar on a browser with system tz set to LA; appointment created in salon tz still renders correctly.

---

### Phase 3: Cleanup and decommission

- U9. **Cron rationalization**

**Goal:** Port `ArchiveAppointments.py` to psycopg; delete the two crons whose purpose is now schema-enforced.

**Requirements:** R12, R13

**Dependencies:** U7 (Postgres appointments live)

**Files:**
- Modify: `cron/ArchiveAppointments.py` (rewrite using psycopg; loops over all organizations from day one — `SELECT id FROM organizations`, then for each org: `UPDATE appointments SET archived_at = now() WHERE ends_at < now() - interval '30 days' AND archived_at IS NULL AND organization_id = %s`; logs `archived N rows for org <uuid>` per iteration. Reads `POSTGRES_URL` env var only — no `CRON_ORG_ID` to forget when salon-2 onboards.)
- Modify: `cron/pyproject.toml` (replace `pymongo` with `psycopg[binary]`; add `pytest` as a dev dependency; remove deps the deleted scripts needed; keep `python-dotenv`)
- Modify: `cron/requirements.txt` (if still used — sync with `pyproject.toml`)
- Create: `cron/test_archive_appointments.py` (pytest + psycopg integration test against dev Postgres)
- Delete: `cron/AppointmentsSameStartEndTime.py`
- Delete: `cron/MergeDuplicateClients.py`
- Modify: `cron/README.md`
- Modify: `docs/modules/cron.md` (note in U11; reflect that only one script remains)

**Approach:**
- The exact cutoff (30 days, 60 days, etc.) should match the current Python script's behavior. Read it before rewrite to preserve. The current script's MongoDB query semantics translate to "any appointment ending more than N days ago and not yet archived."
- Connection string read from `.env` via `python-dotenv`. Same pattern as `migration/`.
- **Loop over all organizations from day one.** Hibernate `@TenantId` does NOT apply to this Python script (it's a different process with no Spring context). Script enumerates orgs via `SELECT id FROM organizations`, then issues the archive UPDATE per org with `AND organization_id = %s`. No `CRON_ORG_ID` env var — when salon-2 onboards, the cron just picks them up. Per-org row counts logged so a silently-stopped archive is visible in retrospect.
- Add a small automated test (`cron/test_archive_appointments.py` using pytest + psycopg against the same dev-stack Postgres) that seeds one row past the cutoff and one inside it, invokes the archive function, and asserts the column state. This satisfies R14's real-Postgres intent for the one remaining cron path; relying on manual smoke testing alone is inconsistent with the rest of the plan's test bar.
- Smoke test: run locally against the migrated Postgres after U7, observe `archived_at` populated for old rows and nothing else changed.

**Patterns to follow:**
- `migration/migrate_mongo_to_postgres.py` — psycopg connection setup pattern.
- `cron/README.md` — preserve "what this directory is for" framing; update specifics.

**Test scenarios:**
- Happy path: with the test Postgres seeded with one appointment whose `ends_at` is 31 days ago and `archived_at IS NULL`, run the script; verify `archived_at` is now non-null and approximately `now()`.
- Edge case: with an appointment whose `ends_at` is 29 days ago, the same run does NOT archive it.
- Edge case: with an appointment that is already archived (`archived_at` set), the run does NOT update `archived_at` (idempotent).
- Error path: with `POSTGRES_URL` unset, the script exits non-zero with a clear message.

**Verification:**
- Script runs cleanly against the migrated dev Postgres.
- `git status` confirms `AppointmentsSameStartEndTime.py` and `MergeDuplicateClients.py` are deleted.

---

- U10. **Mongo decommission**

**Goal:** Remove Mongo from the application entirely. No `@Document` entities, no `spring-boot-starter-data-mongodb`, no `mongodb-driver-sync`, no `MongoConfig`, no Mongo env vars. Compilation is now Postgres-only.

**Requirements:** R4, R19

**Dependencies:** U2, U3, U4, U5, U6, U7, U8, U9 (everything must be on Postgres before Mongo can be removed)

**Files:**
- Modify: `api/pom.xml` — remove `spring-boot-starter-data-mongodb`, `mongodb-driver-sync` dependency + the `${mongodb.version}` property, remove related pin warnings.
- Delete: `api/src/main/java/com/nail_art/appointment_book/entities/Counter.java`
- Delete: `api/src/main/java/com/nail_art/appointment_book/repositories/CounterRepository.java`
- Delete: `api/src/main/java/com/nail_art/appointment_book/services/CounterService.java`
- Delete: `api/src/test/java/com/nail_art/appointment_book/services/CounterServiceTest.java`
- (`MongoConfig.java` already deleted in U6; not touched here.)
- Modify: `api/src/main/resources/application.properties` — remove the `MongoAutoConfiguration` / `MongoDataAutoConfiguration` exclusion from `@SpringBootApplication` (now unnecessary; the deps are gone). Confirm all `spring.data.mongodb.*` keys removed (already done in U2).
- Modify: `api/src/main/resources/application-dev.properties` — confirm `spring.data.mongodb.uri` removed (already done in U2).
- Modify: `api/src/main/resources/.env.example` — already updated; verify.
- Modify: `AGENTS.md` — remove the "Use `CounterService.getNextSequence(<collection>)`" guidance; remove `mongodb-driver-sync` pin note; replace MongoDB references with PostgreSQL; add Hibernate filter and `useMe()` notes. **Lands at U10 (not U11) so the router does not give actively-wrong guidance during the U2-U9 build span.**
- Verify: any other `@Document` annotation, `MongoRepository` import, or `org.springframework.data.mongodb` import is gone (compilation will fail otherwise).
- External: Render dashboard — remove `PROD_MONGO_URI` and `DEV_MONGO_URI` env vars on the API service. Documented in U12 runbook, not changed in this PR.

**Approach:**
- This unit is destructive and depends on every other unit being green. Run it last in the implementation order.
- Compilation is the primary verification: if the project still compiles after deleting these files, all references are gone. If it fails, find the stragglers.
- Render env var deletion is operational, not code — it happens during the cutover per U12.

**Test scenarios:**
- *Test expectation: none — destructive cleanup. Verified by U2–U9 tests continuing to pass post-deletion, compilation succeeding, and zero `org.springframework.data.mongodb` or `com.mongodb` references remaining anywhere.*

**Verification:**
- `./mvnw clean test` passes.
- `grep -r mongodb api/src` returns zero hits.
- `grep -r Mongo api/src` returns zero hits.
- `./mvnw dependency:tree | grep -i mongo` returns zero hits (catches transitive Mongo deps that the src-only grep would miss).
- `cd client && npm run build` still passes (no client-side Mongo refs expected, but verify).
- Render env var removal is captured in the U12 runbook so the cutover-day operator deletes them at the right moment.

---

- U11. **Documentation refresh**

**Goal:** Update every doc that mentions Mongo, Counter, the old appointment shape, or the old auth model to reflect the Postgres-only world. Add the new patterns (Hibernate filter, `useMe`, dayjs tz discipline) as guidance for future contributors.

**Requirements:** R20

**Dependencies:** U2, U3, U7, U8, U10

**Files:**
- Modify: `README.md` (project overview if it mentions Mongo)
- (`AGENTS.md` is updated in U10, not here — it's the contributor router and its accuracy matters throughout the build span, not just at the end.)
- Modify: `docs/INDEX.md` (no structural change expected; update any prose that says "MongoDB")
- Modify: `docs/modules/api.md` (rewrite Persistence section for JPA/Flyway/Hibernate filter; update endpoint table for UUID path params and new appointment shape; remove Counter references; remove MongoDB driver pin lesson)
- Modify: `docs/modules/client.md` (add `useMe()` and dayjs tz pattern; appointment field shape note)
- Modify: `docs/modules/cron.md` (now describes one script, references Postgres)
- Modify: `docs/reference/architecture.md` (data-store row changes from MongoDB to PostgreSQL; multi-tenant note)
- Modify: `docs/reference/conventions.md` (UUID-as-id convention; org-scoped queries enforced by filter)
- Modify: `docs/reference/lessons.md` (mark Counter lessons as "encoded in schema — historical"; update auth lessons to reflect JWT carrying `org` and `role` claims; mark `mongodb-driver-sync` operations lesson as historical; rewrite "Time slot conflict checks" lesson to reference the interval-based logic; rewrite "End time must be after start time" lesson to reference the DB CHECK; mark "Date display needs explicit timezone" lesson as superseded by the dayjs `.tz(orgTz)` discipline)
- Modify: `docs/reference/local-development.md` (Compose stack already includes Postgres; remove any remaining Mongo Atlas notes; update env var samples)
- Modify: `docs/updates.md` (add accretive entry for the migration)
- Modify: `docs/reference/deployment.md` (Render Postgres setup note, env vars)

**Approach:**
- Treat AGENTS.md and `docs/reference/lessons.md` as the highest-leverage docs (router + invariants) — make sure those are fully updated. Other modules can be lighter.
- Lessons that are now schema-encoded should NOT be deleted — they remain as history. Mark them as "encoded in code" so future contributors know not to expect application-layer guards for them.
- Add a new lesson capturing the migration itself: "Mongo→Postgres cutover, 2026-05" — link to this plan and the origin requirements doc.

**Patterns to follow:**
- `AGENTS.md` Maintenance & Accretion guidance: keep AGENTS.md lean, push detail to `docs/`.
- `docs/reference/lessons.md` format: Symptom / Constraint / commit ref. For new entries, link the plan path.

**Test scenarios:**
- *Test expectation: none — documentation. Verified by: every file mentioning "MongoDB", "@Document", "CounterService", or `mongodb-driver-sync` either updates the reference or marks it as historical with explicit reasoning. `grep -ri mongo docs/ AGENTS.md README.md` returns only intentional historical references.*

**Verification:**
- `grep -ri mongo docs/ AGENTS.md README.md` returns zero unintentional hits.
- AGENTS.md still under 200 lines (existing constraint).
- `docs/INDEX.md` still routes correctly.

---

- U12. **Cutover runbook**

**Goal:** Document the operational sequence for cutover day so the process is repeatable, reviewable, and reversible. This is not code; it's the durable record of how to actually perform R17, R18, R19, R21.

**Requirements:** R17, R18, R19, R21, F1

**Dependencies:** Drafted any time; finalized after U10 so the env-var-removal step matches reality.

**Files:**
- Create: `docs/operations/postgres-cutover-runbook.md` (new directory `docs/operations/` if it doesn't exist)
- Modify: `docs/INDEX.md` (add a link to the runbook under Operations)
- Modify: `docs/updates.md` (note the runbook was added)

**Approach:**
- Runbook is a checklist, not prose. Sections:
  1. **Pre-cutover (T-1 day):** re-run `migration/audit_mongo.py` against prod Mongo; resolve any new blockers. Identify the Mongo service id for the "Unavailable" service so the migration step can pass `--unavailability-service-mongo-id <id>`.
  2. **Pre-cutover (T-1 hour):** add laptop IP to Render Postgres allowlist; grab external `POSTGRES_URL`; export prod `MONGO_URI`; run `migrate_mongo_to_postgres.py --dry-run --unavailability-service-mongo-id <id> <other args>`; compare row counts to prod Mongo counts; spot-check that the dry-run output reports the unavailability marker is set on the expected row.
  3. **Cutover (T):** run `migrate_mongo_to_postgres.py --unavailability-service-mongo-id <id> <other args>` (commits); deploy the new (Mongo-free) API image. **Adding staff accounts post-cutover:** the `POST /users` endpoint is shipped without a UI in this branch. To add a staff account, the developer invokes it via curl with an owner token: `curl -X POST <API>/users -H "Authorization: Bearer <owner-token>" -H "Content-Type: application/json" -d '{"username":"<staff>","password":"<password>","role":"staff"}'`. Admin UI lights it up in a follow-up branch. **Scheduling constraint: pick a cutover window during closed hours that does NOT span the 3 PM ET SMS reminder cron** — either run before the cron tick or after it, so the cron either runs entirely on the old image or entirely on the new one. Scaling Render API to zero is intentionally omitted: with salons closed there are no human writes to lose, and the brief deploy-rolling-window between old+new images is acceptable. **Env vars (`PROD_MONGO_URI`, `DEV_MONGO_URI`) stay in place** during this step so the rollback path (redeploy prior image) is a single action without env-var restoration from memory.
  4. **Post-cutover (T+15min):** salon owner walks the manual E2E checklist (login with new owner credentials → today's calendar → create test appointment → edit → delete → search by phone → create an appointment using the Unavailable service and verify the special color renders on both mobile and desktop calendars → log out → log back in). **Remove laptop IP from the Postgres allowlist as part of this step** — solo-operator responsibility, no auto-revoke.
  5. **Post-cutover (T+1 day):** rollback window closed. Remove `PROD_MONGO_URI` and `DEV_MONGO_URI` from Render env vars. Pause Mongo Atlas cluster.
  6. **Rollback path:** if cutover fails — either (a) revert the cutover merge in git and redeploy prior API image, or (b) reset `main` to the prior known-good commit and redeploy prior API image. Both work because solo dev controls all merges to `main` and no other commits will land during the cutover window. Un-pause Atlas if it was paused. Mongo data was never written to during cutover (the migration script reads from Mongo, writes to Postgres only), so no Mongo restore is needed.
  7. **Deletion (T+2 weeks):** delete the Mongo Atlas cluster.
- The migration script's `--dry-run` mode is safe — the script wraps everything in a single transaction and explicitly rolls back. A dropped connection without commit also rolls back (Postgres default). The script is destructive in commit mode; only run it during the actual cutover window.
- Include exact commands but no secrets. Use placeholders like `<EXTERNAL_POSTGRES_URL>` and `<RENDER_SERVICE_ID>`.
- Include the manual E2E checklist verbatim as a copy-pasteable list.

**Patterns to follow:**
- `docs/reference/deployment.md` for cross-references.
- `docs/reference/local-development.md` style for command blocks.

**Test scenarios:**
- *Test expectation: none — runbook documentation. Verified by: a fresh reader (the developer) can execute the cutover steps in order without needing to consult the brainstorm or plan docs. Dry-run rehearsal of the runbook against the dev Postgres before real cutover is the integration test.*

**Verification:**
- Runbook is complete enough to perform cutover end-to-end.
- Linked from `docs/INDEX.md` so it's discoverable.

---

## System-Wide Impact

- **Interaction graph:** `JwtAuthenticationFilter` → principal → `TenantContextWebFilter` → `TenantContext` ThreadLocal → Hibernate `CurrentTenantIdentifierResolver` → `@TenantId` discriminator predicate on every JPA query in every service. `useMe()` → every component that renders an appointment timestamp. SMS scheduler (`@Scheduled` in `SmsService`) → `getAppointmentsForTomorrow` → Postgres in salon-tz semantics → Twilio. Render external Postgres → migration script → prod Postgres → API service (in cutover sequence).
- **Error propagation:** `DataIntegrityViolationException` replaces `DuplicateKeyException` at the global handler boundary; user-visible 409 unchanged. Validation errors at the controller boundary still return 400 with field maps. Filter-hidden rows surface as 404 from `findById` — controllers must not leak the difference between "doesn't exist" and "exists in another org."
- **State lifecycle risks:** Refresh-token table is keyed differently post-cutover — existing sessions are invalid on the first request after deploy, which is expected (F2). Migration script is idempotent via TRUNCATE CASCADE; rerunning is safe pre-cutover but should NOT be rerun post-cutover (that would wipe live data). U12 runbook calls this out explicitly.
- **API surface parity:** All authenticated endpoints change ID type (`int` → UUID string) and Appointment field shape (`date`/`startTime`/`endTime` → `startsAt`/`endsAt`). No interface preserves the old shape. SMS scheduling, Twilio integration surface, refresh-cookie attributes, and CORS configuration are all unchanged.
- **Integration coverage:** Cross-org isolation (AE4) is not provable from per-domain unit tests — covered by `CrossOrgIsolationIntegrationTest` (U7, end of Phase 2) with two seeded orgs across every domain. The Phase 1 `TenantContextIntegrationTest` (U3) is a structural stand-in against the User/OrganizationUser entities only. Form-to-display tz round-trip (AE1) is not provable from backend tests alone — covered by `client/src/utils/datetime.test.ts` (U8) plus a manual cross-device check on cutover day.
- **Unchanged invariants:** Refresh-cookie attributes (`HttpOnly; SameSite=None; Secure; maxAge=30d`). Anonymous endpoint set (`/auth/**` only). Pagination cap `2000` on `/clients`. Search semantics (partial, case-insensitive on `name`/`phoneNumber`). `markReminderSent` does not route through `editAppointment`. SMS scheduler cron `0 0 15 * * *` zone `America/New_York`. `client/.npmrc` `min-release-age=7d` pin.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Conflict-detection parity regression — new interval logic produces a different verdict than old string-compare on an edge case used in production. | Test scenarios AE2 + boundary cases (exact-touch intervals `[10:00-11:00]` vs `[11:00-12:00]` allowed, `[10:30-11:30]` rejected). Manual E2E pass on cutover day. |
| Timezone discipline drift — a future contributor adds a `dayjs(...).format(...)` without `.tz(orgTz)`. | Single helper module routes all formatting; helpers THROW on undefined `orgTz` rather than silently falling back to browser tz; `<RequireMe>` boundary guarantees the helper is never called with undefined; code-review convention. |
| Migration row-count discrepancy at cutover — the prod Mongo data shifted between local dev rehearsal and cutover day. | U12 mandates a dry-run immediately before the real run with a row-count comparison to prod Mongo as a gate. |
| Hibernate filter activation soundness — original `HandlerInterceptor` + `@Filter` plan was broken. | **Resolved:** switched to Hibernate 6 `@TenantId` discriminator-based tenancy. `@TenantId` covers `EntityManager.find()`, derived queries, and criteria queries (the failure modes of `@Filter`). Verified at Phase 1 exit (`TenantContextIntegrationTest` exercises the discriminator on a seeded `@TenantId` entity) and Phase 2 exit (`CrossOrgIsolationIntegrationTest` across all domains). See U3. |
| SMS scheduler tenant scoping — `@Scheduled` jobs have no HTTP request context. | **Resolved:** `SmsService.sendReminders()` iterates orgs and wraps each org's batch in `TenantContext.runAs(orgId, ...)`. Loop of size 1 today; expanding to N orgs is a config change in the follow-up branch. Per-appointment transaction granularity preserves today's failure semantics (Twilio failure on one appointment doesn't poison the rest). See U7. |
| `@Query(nativeQuery=true)` bypass risk — Hibernate `@TenantId` doesn't auto-rewrite hand-written SQL. | **Accepted:** zero native queries in the repo today; first one added must include an explicit org predicate (code-review gate). Re-evaluate when a real use case appears. The deepening pass added Postgres RLS specifically to cover this; reversed in document review because (a) the bypass surface is hypothetical, (b) the cost of RLS carve-outs outweighed the benefit, (c) solo-dev code review is sufficient. See Alternative Approaches Considered for the full rejection rationale. |
| Bootstrap entity leakage — `User` / `OrganizationUser` / `RefreshToken` are not `@TenantId`-annotated, so a future query against them could return cross-tenant rows. | **Accepted with explicit boundary:** login + refresh paths only. Any new query against these tables that returns more than the authenticated user's own rows is a code-review red flag. Documented in U3 Approach. |
| `appointment_services` junction modeling — composite-FK enforcement requires the explicit `@Entity + @EmbeddedId` path. | Committed in Key Technical Decisions and U7 Approach. Repository tests cover cascade behavior. |
| Render Postgres external URL access failing during cutover. | U12 runbook includes a verification step (`psql` against the external URL with the laptop IP allowlisted) before scaling API to zero. |
| `getAppointmentsForTomorrow` returning wrong rows due to server-vs-salon tz confusion. | AE3 test in U7; manual SMS dry-run in dev before cutover. |
| Mongo decom (U10) breaks compilation because a `@Document` or `MongoRepository` import slipped past earlier units. | U10 verification step is `grep -r mongodb api/src` → zero hits + `./mvnw clean test` passes. Mongo autoconfig already excluded at U2, so boot stays healthy throughout the build span. |
| One-branch rollback if `main` advances during the cutover window. | **Resolved 2026-05-25 (no change needed).** Solo dev controls all merges to `main`; no other commits will land during cutover because the user is the only one who can push. Rollback = revert the cutover merge OR reset main to the prior known-good commit. U12 runbook documents both options. |
| Frontend regression hiding in AppointmentModal because the form's reliance on `form.date + form.startTime` is hardcoded in many call sites. | U8 file list enumerates every affected call site (date-picker `value=` props, `onChange` handlers, end-time validation, services multi-select, createApp initial state). Vitest harness added in U1 enables regression tests. |

---

## Documentation / Operational Notes

- All `docs/` updates concentrated in U11 to keep the doc PR review surface coherent.
- Cutover runbook (U12) is the operational artifact that survives this branch. Should be referenced by future ops docs.
- `migration/` directory is intentionally kept in the repo through cutover; can be deleted in a separate post-cutover cleanup PR (mentioned in `migration/README.md` already).
- Render env var changes documented in U12 — operator follows the runbook on cutover day; no code change tracks them.

---

## Alternative Approaches Considered

- **Auth slice last (F4 original order):** Origin doc proposed building Employees → Services → Clients → Appointments → Auth, on the rationale that "auth touches every surface." Rejected for sequencing: doing auth last requires either (a) keeping the Mongo-backed auth running alongside Postgres domain code (mixed state, awkward principal shape, frontend still has no `useMe`), or (b) scaffolding a hardcoded-org-id provider in U2 that must be swapped out in the final unit. Auth-first eliminates both costs: every later slice uses real authenticated principals and the real Hibernate filter, no scaffolding to remove.
- **Strangler-fig dual-write (write to both Mongo and Postgres during a transition period):** Rejected. Single-tenant, single-developer, low-traffic app; the dual-write complexity (consistency between two stores, reconciliation tooling, deciding which is canonical for reads) far exceeds the cost of a single coordinated cutover for a salon that closes at 8 PM.
- **DTO compatibility shim preserving the old API shape:** Rejected per origin Key Decisions — adds throwaway code on both ends.
- **One big PR vs phased PRs into the cutover branch:** Considered phased PRs (U1-U3 first, U4-U8 second, U9-U12 third) but the merge target is `postgres-migration-cutover` not `main`, so the "phased" structure is just intermediate commits within the same branch. Same atomicity, less ceremony.
- **Original `HandlerInterceptor` + `@Filter` multi-tenancy design:** Rejected after the first document review. The `HandlerInterceptor` `preHandle` runs before the Hibernate Session opens (given `open-in-view=false`), so the filter enablement operates on a Session the actual query never uses. `@Filter` also doesn't cover native queries, `findById`, or criteria queries built outside the Session, undermining the "single enforcement point" claim. `@Scheduled` jobs bypass the interceptor entirely. Replaced by `@TenantId` discriminator-based tenancy described in Key Technical Decisions.
- **App-only multi-tenancy (`@TenantId` alone, no RLS) — CHOSEN.** Originally rejected during the 2026-05-25 deepening pass in favor of dual-layer `@TenantId` + RLS. Reversed during same-day document review. Reasoning for the reversal: (a) the realistic bugs for a solo dev with 1-2 tenants are `@TenantId` wiring mistakes (caught by AE4 integration test) and missed `runAs` in scheduled jobs (RLS doesn't catch these either — it just turns "wrong tenant's data" into "zero rows"); (b) Hibernate 6 `@TenantId` is genuinely different from the rejected `@Filter` design — it covers `EntityManager.find()`, derived queries, and criteria queries; (c) the dual-layer design accumulated carve-outs (`organizations` exempted, `users` permissive policy, login bootstrap special case, migration script owner-bypass, scheduled-job `SET LOCAL` plumbing, role split, four new env vars, DataSource proxy for `SET LOCAL`, GUC handling for unset values) faster than it added correctness; (d) `@Query(nativeQuery=true)` is the strongest argument for RLS but is currently absent from the repo and easy to spot in code review; (e) the user's stated "right-size ops/security for solo dev" preference maps directly onto rejecting the layer of ceremony. The second-salon prep work (`docs/brainstorms/2026-05-24-salon-2-prep-notes.md`) doesn't change this — it changes operator concerns (per-tenant Twilio creds, onboarding CLI) that are orthogonal to RLS.
- **Dual-layer `@TenantId` + Postgres RLS (the deepening-pass design) — REJECTED in document review.** Captured here for the historical record. The reasoning was "structural correctness — impossible to forget." See the chosen-design rationale above for why the reversal landed.
- **AppointmentsService `@ManyToMany` modeling of `appointment_services`:** Rejected. Composite-FK enforcement back to `(services.id, services.organization_id)` requires a non-key `organization_id` column on the junction row, which Hibernate `@ManyToMany` + `@JoinTable` cannot populate. Explicit `@Entity` + `@EmbeddedId` is mandatory.
- **Spring `@Scheduled` for ArchiveAppointments instead of Python cron:** Rejected for minimum-churn reasons. Cron concerns stay in `cron/`; one less moving part to verify on cutover day.

---

## Sources & References

- **Origin document:** [docs/brainstorms/2026-05-24-postgres-cutover-requirements.md](docs/brainstorms/2026-05-24-postgres-cutover-requirements.md)
- Migration script (already committed): `migration/migrate_mongo_to_postgres.py`, `migration/audit_mongo.py`, `migration/README.md`
- Target schema: `api/src/main/resources/db/migration/V1__init_multi_org_schema.sql`
- Lessons that constrain implementation: [docs/reference/lessons.md](docs/reference/lessons.md)
- Existing API surface: [docs/modules/api.md](docs/modules/api.md)
- Existing frontend patterns: [docs/modules/client.md](docs/modules/client.md)
- Router for contributors: [AGENTS.md](AGENTS.md)
