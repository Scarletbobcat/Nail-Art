# Lessons Learned

Hard-won context distilled from prior commits and incidents. Each entry names the symptom, the cause, and the constraint that came out of it. Update this file when a new lesson would have saved an hour of debugging.

## Persistence

### Numeric `id` sequences are app-managed, not Mongo `_id`
- **Symptom**: Re-using IDs after deletes caused duplicate appointment / employee / service IDs. Creating any new record sometimes created a *new* counter document instead of incrementing the existing one (`263fca5`, `fe11b36`, `d908257`).
- **Constraint**: All numeric IDs must go through `CounterService.getNextSequence(<collection>)`. Don't generate IDs in callers, and don't introduce additional counter documents per request.

### Use the right counter name
- **Symptom**: `AppointmentService` was creating clients but pulling from the `"Appointments"` counter, so client IDs collided (`0257cf8`).
- **Constraint**: The counter name passed to `CounterService` must match the collection being written. Reuse the constant exactly.

### Client uniqueness is enforced by phone number
- **Symptom**: 10 duplicate client pairs existed in production (`0257cf8`).
- **Resolution**: Partial-filter unique index on `Client.phoneNumber`, `@PostConstruct` startup verification in `MongoConfig`, `DuplicateKeyException` mapped to `409` in `GlobalExceptionHandler`, and `ClientService.createClient()` checks before insert.
- **Constraint**: Don't bypass `ClientService.createClient`. When linking appointments to clients, look up by phone number, not by trusting the inbound `clientId`.

### Don't carry per-entity bookkeeping fields you never read
- **Symptom**: `Client.appointmentIds` was maintained on every appointment write but never queried, costing extra writes for no value (`0257cf8`).
- **Constraint**: Strip fields that nothing reads. Add them back when there is an actual query.

## Appointments

### Time slot conflict checks live in `AppointmentService` create/edit only
- **Symptom**: SMS reminders were silently failing because `sendReminders` was calling `editAppointment` to set `reminderSent=true`, which triggered the full per-employee/per-date conflict check (`c281523`).
- **Constraint**: For internal field flips, use the dedicated method (`markReminderSent`). Do not route administrative updates through user-facing edit paths.

### End time must be after start time
- **Symptom**: Appointments with identical start/end times slipped into the DB (`5fc7690`, `938f13f`, `e02f55a`).
- **Constraint**: Enforce `endTime > startTime` server-side and surface the validation error in modal banners. `cron/AppointmentsSameStartEndTime.py` exists as a backstop check.

### Date display needs an explicit timezone on the search page
- **Symptom**: Off-by-one day on the search results (`d96f984`).
- **Constraint**: Use `timeZone: "UTC"` when formatting raw date strings from MongoDB. Don't rely on the browser default.

## Authentication & sessions

### Refresh-token cookie needs `maxAge`
- **Symptom**: Users were logged out as soon as the browser closed because the refresh cookie defaulted to a session cookie (`8befafd`).
- **Constraint**: Keep the 30-day `maxAge` on the `refreshToken` cookie and the matching `SameSite=None; Secure` flags. Both ends of `/auth/login`, `/auth/refresh`, and `/auth/logout` must agree.

### Username matching is case-insensitive
- **Symptom**: Users couldn't log in because they capitalized differently than at signup (`8b0a128`).
- **Constraint**: `UserRepository.findByUsernameIgnoreCase` is the only correct lookup. `CustomUserDetailsService` and `AuthenticationService` must both use it.

### Don't carry stale auth across profile switches
- **Symptom**: Switching the API from `prod` to `dev` caused a constant login loop because old configuration tried to use mismatched cookies/CORS (`f459bf3`).
- **Constraint**: After changing `spring.profiles.active`, clear localStorage and the `refreshToken` cookie on the client. Treat dev and prod as fully separate auth realms.

## Frontend

### Search uses partial case-insensitive matching everywhere
- **Symptom**: Client search required exact matches (`b6add2c`).
- **Constraint**: Client search uses regex on `name` and `phoneNumber`; appointment search uses `findByPhoneNumberContaining`; employees/services use `ContainingIgnoreCase`. Don't reintroduce exact-match lookups.

### Frontend and backend pagination caps must match
- **Symptom**: Client dropdown was capped at 100 while DB held more, so autofill missed clients (`d96f984`).
- **Constraint**: The size param on the client side and the server-side cap on `/clients` must agree (currently `2000`). Change both together.

### Build the frontend before committing
- **Symptom**: `framer-motion` `Variants` type changes broke the Render build only at deploy time (`8befafd`).
- **Constraint**: Run `cd client && npm run build` (which runs `tsc -b && vite build`) before committing frontend changes. CI also runs `tsc -b --noEmit` and `npm run lint`.

### Supply-chain safety: pin via `.npmrc`
- The repo ships `client/.npmrc` with `min-release-age=7d` (`d96f984`). Don't remove it; it gives a cushion before pulling in newly published, possibly compromised packages.

## SMS reminders

### Scheduled, not manual
- **History**: Reminders started as a manual button (`383ba5e`), then moved to `@Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")` and were later shifted from 10 AM to 3 PM ET (`92db05e`).
- **Constraint**: There is exactly one place reminders are dispatched (`SmsService#sendReminders`). Don't wire UI buttons that fan out the same Twilio calls.

### Respect Twilio unsubscribe (code 21610) and retry transient failures
- `SmsService` short-circuits on Twilio error `21610` and retries on `5xx` / `429` with `MAX_ATTEMPTS=3` and a 5s backoff (`SmsService.java`).
- Phone numbers in logs are masked to the last four digits.

## Operations

### Render needed an explicit MongoDB driver pin
- **Symptom**: SSL handshake failures on Render (`0efe8ac`).
- **Constraint**: `mongodb-driver-sync` is pinned to `${mongodb.version}` (currently `5.2.1`) in `api/pom.xml`. Don't downgrade or rely on the Spring Boot starter's transitive version.

### CI gates the things that actually broke before
- Backend service tests (`com.nail_art.appointment_book.services.**`) and frontend `tsc -b --noEmit` + `npm run lint` run on every PR (`.github/workflows/ci.yml`, added in `0257cf8`).
- **Constraint**: New service-layer behavior gets a service test. New frontend pages get clean lint + typecheck before they merge.

## Documentation hygiene

- Brainstorm / requirements drafts live in `docs/brainstorms/` and stay out of feature commits.
- When a non-obvious lesson shows up in a commit message ("fix bug where …"), copy the why here so the next person doesn't have to grep history.

## Maintenance & Accretion

Append new lessons whenever a fix's commit message would have saved real time if it had been here first. Cite the commit short SHA. Trim or merge lessons that have been fully encoded into code (typed APIs, tests, schema constraints) so they no longer need to live as prose.
