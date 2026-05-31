# Lessons Learned

Hard-won context distilled from prior commits and incidents. Each entry names the symptom, the cause, and the constraint that came out of it. Update this file when a new lesson would have saved an hour of debugging.

## Persistence

### Numeric ID counter lessons are historical after the PostgreSQL cutover
- **Symptom**: In the Mongo era, re-using IDs after deletes caused duplicate appointment / employee / service IDs, and one bug pulled client IDs from the wrong collection counter (`263fca5`, `fe11b36`, `d908257`, `0257cf8`).
- **Constraint**: This is now encoded away by PostgreSQL UUID defaults. Do not reintroduce application-managed numeric sequence counters.

### Client uniqueness is schema-enforced by organization and phone number
- **Symptom**: 10 duplicate client pairs existed in production (`0257cf8`).
- **Constraint**: The PostgreSQL schema enforces a per-organization unique phone index. Keep client writes flowing through `ClientService` so 409 handling and appointment phone linking stay consistent.

### Don't carry per-entity bookkeeping fields you never read
- **Symptom**: `Client.appointmentIds` was maintained on every appointment write but never queried, costing extra writes for no value (`0257cf8`).
- **Constraint**: Strip fields that nothing reads. Add them back when there is an actual query.

### `@TenantId` does not make primary-key lookups safe
- **Symptom**: During the PostgreSQL migration, `EntityManager.find(Employee.class, orgBEmployeeId)` returned another organization's row even with `TenantContext` set to org A.
- **Constraint**: Tenant-owned repositories must use explicit scoped JPQL/derived lookup methods such as `findScopedById(UUID)` instead of default `JpaRepository.findById`/`EntityManager.find`. Derived and criteria queries are covered by `@TenantId`; primary-key lookups are not a tenant boundary in Hibernate 6.5.

### Bootstrap/auth tables intentionally do not use `@TenantId`
- **Symptom**: Applying tenant discrimination to bootstrap tables would make login, refresh-token lookup, and organization bootstrap depend on a tenant that is not yet known.
- **Constraint**: `User`, `Organization`, `OrganizationUser`, `OrganizationSettings`, and `RefreshToken` stay outside `@TenantId`. Access them through auth-specific services, explicit membership checks, or script-only operational paths.

### Mongo to PostgreSQL cutover is historical context
- **Symptom**: The old stack mixed Mongo documents, numeric IDs, single-tenant assumptions, and timezone-naive appointment fields.
- **Constraint**: The cutover is intentionally atomic: JPA/Flyway/PostgreSQL, UUID IDs, Hibernate `@TenantId`, `{startsAt, endsAt}` appointment timestamps, `useMe()` org timezone, and psycopg cron all land together.

## Appointments

### Time slot conflict checks use interval overlap
- **Symptom**: The old wall-clock string model made conflict checks and date boundaries too easy to get subtly wrong.
- **Constraint**: Create/edit checks compare intervals: existing start before new end and existing end after new start for the same employee. Internal field flips such as reminder sent still use dedicated methods and do not route through user-facing edit paths (`c281523`).

### End time after start is a database invariant
- **Symptom**: Appointments with identical start/end times slipped into the DB (`5fc7690`, `938f13f`, `e02f55a`).
- **Constraint**: The PostgreSQL `appointments` table has `check (ends_at > starts_at)`. Keep UI/server validation friendly, but rely on the schema as the final backstop.

### Appointment display uses organization timezone
- **Symptom**: Off-by-one day bugs appeared when display code relied on the wrong timezone (`d96f984`).
- **Constraint**: The frontend formats appointment timestamps through `client/src/utils/datetime.ts` with `.tz(orgTz)`, where `orgTz` comes from `useMe().organization.timezone`. Do not use browser timezone as a business-date fallback.

## Authentication & sessions

### Refresh-token cookie needs `maxAge`
- **Symptom**: Users were logged out as soon as the browser closed because the refresh cookie defaulted to a session cookie (`8befafd`).
- **Constraint**: Keep the 30-day `maxAge` on the `refreshToken` cookie and the matching `SameSite=None; Secure` flags. Both ends of `/auth/login`, `/auth/refresh`, and `/auth/logout` must agree.

### Refresh tokens are per-device sessions
- **Symptom**: Logging in on a second device deleted the first device's refresh token, so the first device was forced back to login when its access token expired.
- **Constraint**: Login must insert a new hashed refresh-token row instead of deleting by `user_id`. `/auth/logout` revokes only the current cookie's row; add a separate logout-all path if the product needs it.

### Username matching is case-insensitive
- **Symptom**: Users couldn't log in because they capitalized differently than at signup (`8b0a128`).
- **Constraint**: PostgreSQL `citext` now enforces case-insensitive username lookup. Keep repository/service code using the username column directly; do not add case-sensitive alternate paths.

### JWT membership cross-check distinguishes bad credentials from auth infrastructure failure
- **Symptom**: A token can be structurally valid while the user's organization membership has been removed or the database is unavailable.
- **Constraint**: `JwtAuthenticationFilter` validates `sub` (user UUID), `org` (organization UUID), and `role`, then checks `organization_users`. Invalid/missing membership returns `401`; data-access failure during the check returns `503`.

### Don't carry stale auth across profile switches
- **Symptom**: Switching the API from `prod` to `dev` caused a constant login loop because old configuration tried to use mismatched cookies/CORS (`f459bf3`).
- **Constraint**: After changing `spring.profiles.active`, clear localStorage and the `refreshToken` cookie on the client. Treat dev and prod as fully separate auth realms.

## Frontend

### Search uses partial case-insensitive matching everywhere
- **Symptom**: Client search required exact matches (`b6add2c`).
- **Constraint**: Client, appointment, employee, and service search remain partial and case-insensitive. Don't reintroduce exact-match lookups.

### Frontend and backend pagination caps must match
- **Symptom**: Client dropdown was capped at 100 while DB held more, so autofill missed clients (`d96f984`).
- **Constraint**: The size param on the client side and the server-side cap on `/clients` must agree (currently `2000`). Change both together.

### Typecheck frontend changes before committing
- **Symptom**: `framer-motion` `Variants` type changes broke the Render build only at deploy time (`8befafd`).
- **Constraint**: Run `cd client && npx tsc -b --noEmit` before committing frontend changes. CI also runs `tsc -b --noEmit` and `npm run lint`.

### Supply-chain safety: pin via `.npmrc`
- The repo ships `client/.npmrc` with `min-release-age=7d` (`d96f984`). Don't remove it; it gives a cushion before pulling in newly published, possibly compromised packages.

## SMS reminders

### Scheduled, not manual
- **History**: Reminders started as a manual button (`383ba5e`), then moved to `@Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")` and were later shifted from 10 AM to 3 PM ET (`92db05e`).
- **Constraint**: There is exactly one place reminders are dispatched (`SmsService#sendReminders`). Don't wire UI buttons that fan out the same Twilio calls.

### Scheduled jobs must set tenant context explicitly
- **Symptom**: Scheduled code runs outside HTTP request filters, so Hibernate cannot infer an organization from web authentication.
- **Constraint**: Scheduled jobs loop organizations and wrap tenant-owned repository work with `TenantContext.runAs`.

### Respect Twilio unsubscribe (code 21610) and retry transient failures
- `SmsService` short-circuits on Twilio error `21610` and retries on `5xx` / `429` with `MAX_ATTEMPTS=3` and a 5s backoff (`SmsService.java`).
- Phone numbers in logs are masked to the last four digits.

## Operations

### MongoDB driver pin is historical
- **Symptom**: Render previously needed an explicit `mongodb-driver-sync` pin to avoid SSL handshake failures (`0efe8ac`).
- **Constraint**: The runtime API no longer depends on MongoDB. Do not re-add Mongo driver pins unless Mongo support is intentionally reintroduced.

### CI gates the things that actually broke before
- Backend service tests (`com.nail_art.appointment_book.services.**`) and frontend `tsc -b --noEmit` + `npm run lint` run on every PR (`.github/workflows/ci.yml`, added in `0257cf8`).
- **Constraint**: New service-layer behavior gets a service test. New frontend pages get clean lint + typecheck before they merge.

## Documentation hygiene

- Brainstorm / requirements drafts live in `docs/brainstorms/` and stay out of feature commits.
- When a non-obvious lesson shows up in a commit message ("fix bug where ..."), copy the why here so the next person doesn't have to grep history.

## Maintenance & Accretion

Append new lessons whenever a fix's commit message would have saved real time if it had been here first. Cite the commit short SHA. Trim or merge lessons that have been fully encoded into code (typed APIs, tests, schema constraints) so they no longer need to live as prose.
