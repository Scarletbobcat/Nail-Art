---
title: "fix: Prevent and resolve duplicate Client records by phone number"
type: fix
status: active
date: 2026-04-07
deepened: 2026-04-07
---

# fix: Prevent and resolve duplicate Client records by phone number

## Overview

Production MongoDB has 10 duplicate Client document pairs sharing the same `phoneNumber`. This causes `IncorrectResultSizeDataAccessException` when `ClientRepository.findByPhoneNumber()` (which returns `Optional<Client>`) finds multiple matches, crashing appointment creation. The fix involves merging existing duplicates, adding a partial filter unique index on `phoneNumber`, handling duplicate key errors at the application level, and fixing a counter sequence bug.

## Problem Frame

When creating an appointment for an existing client, `AppointmentService.createAppointment()` calls `clientRepository.findByPhoneNumber()` which returns `Optional<Client>`. With duplicate Client documents sharing the same phone number, this query throws `IncorrectResultSizeDataAccessException` because `Optional` expects at most one result. The salon cannot create appointments for any customer who has a duplicate Client record.

Root causes:
1. No unique index on `Client.phoneNumber` -- MongoDB allows duplicates freely
2. `ClientService.createClient()` performs no duplicate check before saving
3. `AppointmentService.createAppointment()` has a TOCTOU race on `findByPhoneNumber`
4. Line 72 of `AppointmentService` uses `counterService.getNextSequence("Appointments")` instead of `"Clients"` when auto-creating clients

## Requirements Trace

- R1. All 10 existing duplicate Client pairs must be merged into single records
- R2. All Appointment documents referencing merged-away clients must be updated to the surviving client
- R3. A partial filter unique index on `Client.phoneNumber` must prevent future duplicates (excluding null/empty values)
- R4. `DuplicateKeyException` must be caught and returned as a meaningful HTTP error
- R5. The counter sequence bug on `AppointmentService:72` must be fixed
- R6. The Clients counter must be re-synced to the actual max client ID after migration

## Scope Boundaries

- Phone number format normalization (e.g., "330-506-0180" vs "3305060180") is out of scope
- Making `CounterService` atomic (replacing read-modify-write with `findAndModify`) is out of scope
- Frontend changes to `ClientSelect` or `ClientModal` are out of scope
- Adding `@Valid` to `editClient` controller is out of scope (pre-existing gap)

## Context & Research

### Relevant Code and Patterns

- `User.java:21` -- existing precedent for `@Indexed(unique = true)` in this codebase
- `application.properties:7` -- `spring.data.mongodb.auto-index-creation=true` is enabled, so `@Indexed` annotations auto-create indexes on startup
- `MongoConfig.java` -- existing config class where a programmatic index can be created via `@PostConstruct`
- `GlobalExceptionHandler.java` -- catches all `Exception.class`, uses `ProblemDetail` responses, instanceof chain pattern
- `ClientService.createClient()` -- no phone number uniqueness check
- `AppointmentService.createAppointment():65-83` -- auto-creates clients with wrong counter sequence
- `CounterService.getNextSequence()` -- non-atomic but out of scope for this fix

### Key Observations from Production Data

- 10 duplicate pairs, all with exactly 2 records per phone number
- Two creation patterns: bulk import on 2025-01-03 (created seconds apart) and gradual re-creation over months
- Some pairs have identical names (e.g., "Diane Perica" / "Diane Perica"), others differ (e.g., "Cheryl" / "Cheryl Harper")
- The `ArchiveAppointments.py` cron deletes old appointments without cleaning up `Client.appointmentIds`, so stale IDs exist in those lists
- The Clients counter is at 247 but some clients have IDs >11000 (from the Appointments counter bug)

## Key Technical Decisions

- **Partial filter index over sparse index:** A sparse unique index would block multiple clients with `phoneNumber = ""` (used for walk-ins). A partial filter index with `{ phoneNumber: { $type: "string", $gt: "" } }` enforces uniqueness only on non-empty phone numbers. This cannot use `@Indexed` annotation and requires programmatic index creation in `MongoConfig`.

- **Index created in migration script, verified on app startup:** The migration script creates the partial filter unique index as its final step after verifying zero duplicates remain. This eliminates the deployment race window (no gap between migration and code deploy where new duplicates could slip in). The `MongoConfig` `@PostConstruct` serves as a redundant safety net that verifies the index exists on every startup (idempotent). Since partial filter indexes can't be expressed with `@Indexed`, both the migration script and the Java code use programmatic index creation.

- **Keeper selection: lower ID client survives:** The lower-ID client typically has more appointments and was created first. For name discrepancies, use the longer/more complete name (e.g., "Cheryl Harper" over "Cheryl").

- **Reconstruct appointmentIds from actual Appointments:** Rather than naively unioning both clients' `appointmentIds` lists (which contain stale entries from archived appointments), query `Appointments` by `clientId` after the merge to build an accurate list.

- **Migration script in Python using pymongo:** The project already has Python cron scripts using pymongo in `/cron/`. Follow the same pattern for the migration script.

- **Deployment order: migrate first, then deploy code:** The migration script merges duplicates and creates the unique index as its final step. The code deploy can then happen at any time -- the `@PostConstruct` index creation is a no-op if the index already exists. This eliminates the deployment coordination risk.

- **App startup is intentional fail-fast:** If the `@PostConstruct` index creation encounters duplicates (i.e., migration was not run), the app will fail to start with a `BeanCreationException`. This is intentional -- the application should not run with data that violates the uniqueness invariant.

- **Migration uses transactions per merge pair:** Each duplicate pair merge (update appointments, reconstruct appointmentIds, delete victim) is wrapped in a MongoDB transaction for atomicity. Atlas replica sets support multi-document transactions. If a pair merge fails, it rolls back cleanly and the script can be re-run.

- **Counter re-sync uses $max to avoid race conditions:** The migration updates the Clients counter using an update with `$max` operator, which only increases the counter value, never decreases it. This is safe even if the app creates clients concurrently during migration.

- **Spring Data MongoDB partial filter API note:** The `Criteria` class does not natively support `$type` + `$gt` together for `PartialIndexFilter`. The implementation will need to construct a raw `org.bson.Document` for the filter expression rather than using the `Criteria` builder.

## Open Questions

### Resolved During Planning

- **How to handle empty phone numbers with unique index?** Resolved: Use partial filter index that excludes empty strings and nulls.
- **What name should the surviving client keep?** Resolved: Use the longer/more complete name from either record.
- **Where to put the migration script?** Resolved: In `/cron/` following existing pymongo script patterns.
- **Should the migration reconstruct appointmentIds?** Resolved: Yes, query actual Appointments rather than unioning stale lists.

### Deferred to Implementation

- **Exact partial filter expression syntax in Java:** Spring Data MongoDB's `Criteria` class does not natively chain `$type` and `$gt` for `PartialIndexFilter`. The implementer will need to construct a raw `org.bson.Document` (e.g., `Document.parse("{ phoneNumber: { $type: 'string', $gt: '' } }")`) for the filter expression. The pymongo syntax is straightforward.
- **Whether the app startup gracefully handles index-already-exists:** Should be verified but MongoDB's `createIndex` is idempotent by design.

## Implementation Units

- [ ] **Unit 1: Migration script to merge duplicate clients and create unique index**

  **Goal:** Merge all 10 duplicate Client pairs into single records, update all referencing documents, re-sync the Clients counter, and create the partial filter unique index.

  **Requirements:** R1, R2, R3, R6

  **Dependencies:** None (runs standalone against production MongoDB)

  **Files:**
  - Create: `cron/MergeDuplicateClients.py`

  **Approach:**
  - Take a pre-migration backup (Atlas snapshot or `mongodump` of Clients, Appointments, and ArchivedAppointments collections)
  - Connect to MongoDB using the same connection pattern as `cron/ArchiveAppointments.py`
  - Log pre-migration state: total client count, duplicate count, current Clients counter value
  - Aggregate to find all phone numbers with count > 1
  - For each duplicate group, within a MongoDB transaction:
    - Select keeper (lowest `id`) and victim(s)
    - Choose the longer name between keeper and victim; log both names for operator audit
    - Update all `Appointments` where `clientId == victim.id`: set `clientId = keeper.id`, `name = keeper.name`, `phoneNumber = keeper.phoneNumber`
    - Update all `ArchivedAppointments` where `clientId == victim.id`: set `clientId = keeper.id`, `name = keeper.name`, `phoneNumber = keeper.phoneNumber`
    - Reconstruct keeper's `appointmentIds` by querying `Appointments.find({ clientId: keeper.id })` and collecting the `id` values
    - Delete the victim Client document
  - After all merges, update the `Counters` document for `"Clients"` using `$max` operator to set sequence to `max(Client.id)` -- this only increases the counter, never decreases it, making it safe if the app is running concurrently
  - Verify zero duplicates remain via aggregation query
  - Create the partial filter unique index on `Clients.phoneNumber` with filter `{ phoneNumber: { $type: "string", $gt: "" } }` -- this is the authoritative index creation; the Java `@PostConstruct` is a redundant safety net
  - Make the script idempotent (safe to re-run; if no duplicates found, it skips merging; if index already exists, it skips creation)
  - Log every action: which pairs were merged, name chosen, appointments updated, victims deleted

  **Patterns to follow:**
  - `cron/ArchiveAppointments.py` for MongoDB connection setup, pymongo usage, and script structure

  **Test scenarios:**
  - Run against production: all 10 pairs merged, zero duplicates remain
  - All Appointment documents referencing victim client IDs now reference keeper client IDs
  - All ArchivedAppointment documents referencing victim client IDs are also updated
  - Appointment documents have updated `name` AND `phoneNumber` (not just `clientId`)
  - Keeper's `appointmentIds` list matches actual Appointments in the database
  - Clients counter is set to at least `max(Client.id)`
  - The partial filter unique index exists on the Clients collection
  - Re-running the script produces no changes (idempotent)
  - If a single pair merge fails, it rolls back (transaction) and remaining pairs are still processed

  **Verification:**
  - Run the duplicate-finding aggregation query after migration: returns empty results
  - Spot-check 2-3 merged clients: appointment counts match, names are correct
  - Check Clients counter value matches or exceeds the highest client ID
  - `db.Clients.getIndexes()` shows the partial filter unique index
  - Attempt to insert a duplicate phone number via mongosh: fails with duplicate key error

- [ ] **Unit 2: Add startup index verification in MongoConfig**

  **Goal:** Ensure the partial filter unique index exists on every app startup as a safety net. The migration script (Unit 1) is the primary index creator; this is the redundant declarative guard.

  **Requirements:** R3

  **Dependencies:** Unit 1 (duplicates must be resolved and index created by migration first)

  **Files:**
  - Modify: `api/src/main/java/com/nail_art/appointment_book/configs/MongoConfig.java`

  **Approach:**
  - Inject `MongoTemplate` into `MongoConfig` (it currently only has `MongoClient`)
  - Add a `@PostConstruct` method that creates/verifies the partial filter unique index on `Clients.phoneNumber`
  - Use `MongoTemplate.indexOps("Clients").ensureIndex()` with a raw `org.bson.Document` for the partial filter expression (Spring Data MongoDB's `Criteria` does not support `$type` + `$gt` together for `PartialIndexFilter`)
  - This is idempotent: if the index already exists (created by migration), MongoDB no-ops
  - If duplicates still exist (migration was not run), index creation fails and the app will not start -- this is intentional fail-fast behavior

  **Patterns to follow:**
  - `User.java:21` for the conceptual pattern of unique indexes in this project

  **Test scenarios:**
  - App starts successfully when the index already exists (created by migration)
  - App starts successfully when the index does not exist and no duplicates are present (creates it)
  - App fails to start when duplicates exist (intentional fail-fast)
  - Multiple clients with `phoneNumber = null` or `phoneNumber = ""` can coexist without conflict
  - Re-starting the app does not error on index already existing

  **Verification:**
  - `db.Clients.getIndexes()` shows the partial filter unique index after app startup

- [ ] **Unit 3: Handle DuplicateKeyException in GlobalExceptionHandler**

  **Goal:** Return a meaningful 409 Conflict response when a duplicate phone number is detected, instead of a generic 500 error.

  **Requirements:** R4

  **Dependencies:** Unit 2 (the index must exist for this error to occur)

  **Files:**
  - Modify: `api/src/main/java/com/nail_art/appointment_book/exceptions/GlobalExceptionHandler.java`

  **Approach:**
  - Add a `DuplicateKeyException` check in the instanceof chain, before the generic `Exception` fallback
  - Return HTTP 409 Conflict with a `ProblemDetail` containing a description like "A client with this phone number already exists"
  - Use `org.springframework.dao.DuplicateKeyException` (Spring's wrapper around MongoDB's duplicate key error)

  **Patterns to follow:**
  - Existing instanceof chain pattern in `GlobalExceptionHandler.java` (e.g., `BadCredentialsException` -> 401)

  **Test scenarios:**
  - Creating a client with a duplicate phone number returns 409 with a descriptive message (not 500)
  - Editing a client's phone number to an existing one returns 409
  - Creating an appointment that auto-creates a client with a duplicate phone number returns 409
  - Editing an appointment and changing its phone number to one belonging to a different client returns 409 (the `editAppointment` path propagates phone changes to the Client document)
  - Non-duplicate errors still fall through to 500 as before

  **Verification:**
  - API calls that trigger duplicate key errors return 409 status code with "A client with this phone number already exists" in the response body

- [ ] **Unit 4: Add duplicate check in ClientService.createClient()**

  **Goal:** Check for existing clients by phone number before creating a new one, providing a better UX than relying solely on the database index.

  **Requirements:** R4 (defense in depth)

  **Dependencies:** None (this is a code-level guard independent of the index)

  **Files:**
  - Modify: `api/src/main/java/com/nail_art/appointment_book/services/ClientService.java`

  **Approach:**
  - In `createClient()`, before saving, call `clientRepository.findByPhoneNumber(client.getPhoneNumber())` if the phone number is non-null and non-empty
  - If a match is found, throw an `IllegalArgumentException` or a custom exception with a descriptive message
  - This provides an application-level guard in addition to the database-level unique index
  - Note: The auto-create client path in `AppointmentService.createAppointment()` (lines 65-75) bypasses `ClientService.createClient()` entirely, so this guard does not cover that path. The `AppointmentService` path already has a `findByPhoneNumber` check before creating, and the database unique index serves as the final safety net for any TOCTOU race there. If two concurrent appointment requests race on the same new phone number, one will succeed and the other will get a 409 from the `DuplicateKeyException` handler -- the user retries and it works.

  **Patterns to follow:**
  - The check pattern in `AppointmentService.createAppointment():65-66`

  **Test scenarios:**
  - Creating a client with a phone number that already exists throws an error before reaching the database
  - Creating a client with a new phone number succeeds normally
  - Creating a client with null or empty phone number skips the check and succeeds

  **Verification:**
  - Call `POST /clients/create` with a duplicate phone number: returns an error response before the database save

- [ ] **Unit 5: Fix counter sequence bug in AppointmentService**

  **Goal:** Fix the wrong counter sequence name used when auto-creating clients during appointment creation, and re-sync the Clients counter.

  **Requirements:** R5, R6

  **Dependencies:** Unit 1 (the counter re-sync in the migration handles the current data; this prevents future drift)

  **Files:**
  - Modify: `api/src/main/java/com/nail_art/appointment_book/services/AppointmentService.java`

  **Approach:**
  - Change line 72 from `counterService.getNextSequence("Appointments")` to `counterService.getNextSequence("Clients")`
  - This is a one-line fix

  **Patterns to follow:**
  - `ClientService.createClient():46` which correctly uses `counterService.getNextSequence("Clients")`

  **Test scenarios:**
  - Creating an appointment that auto-creates a client uses the Clients counter, not the Appointments counter
  - The new client's ID is consistent with other clients created through `ClientService.createClient()`

  **Verification:**
  - After creating an appointment that auto-creates a client, check that the Clients counter was incremented (not just the Appointments counter)

## System-Wide Impact

- **Interaction graph:** `AppointmentService.createAppointment()`, `AppointmentService.editAppointment()`, `ClientService.createClient()`, and `ClientService.editClient()` all save Client documents and could trigger the unique index constraint. The `GlobalExceptionHandler` catches the resulting `DuplicateKeyException`.
- **Error propagation:** `DuplicateKeyException` from MongoDB -> Spring's `DuplicateKeyException` -> `GlobalExceptionHandler` -> 409 ProblemDetail -> frontend shows error alert. The frontend already has generic error handling for failed API calls (`setAlert` in `AppointmentModal` and `ClientModal`).
- **State lifecycle risks:** The migration must fully complete before the index is added. If migration is interrupted mid-way, it should be safe to re-run (idempotent). The partial filter index creation on app startup is also idempotent.
- **API surface parity:** No new API endpoints. Error responses change from 500 to 409 for duplicate phone numbers -- this is a behavior improvement, not a breaking change.
- **Cron script impact:** `ArchiveAppointments.py` deletes appointments but does not clean `Client.appointmentIds`. This pre-existing issue is partially addressed by Unit 1's `appointmentIds` reconstruction, but the stale-ID problem will recur over time. Out of scope for this fix.

## Risks & Dependencies

- **Migration must run before code deploy:** If the code deploys while duplicates still exist, the app will fail to start (`@PostConstruct` index creation fails -> `BeanCreationException`). This is intentional fail-fast behavior. Mitigation: The migration script creates the index as its final step, so the code deploy can happen any time after a successful migration.
- **Concurrent requests during migration:** Eliminated as a risk -- the migration script now creates the unique index immediately after merging, with no gap. The `$max` counter update is also safe under concurrent traffic.
- **Client.phoneNumber format inconsistency:** The phone number is stored as-is with no normalization. Two clients with "330-506-0180" and "3305060180" would be treated as different. This is a pre-existing issue, out of scope, but worth noting as a future improvement.
- **editAppointment can change client phone numbers:** `AppointmentService.editAppointment()` propagates appointment name/phone changes to the linked Client document. If a user changes an appointment's phone number to one belonging to a different client, the save will trigger `DuplicateKeyException`. The 409 handler (Unit 3) catches this, but the UX may be confusing (user edited an appointment, not a client).
- **Pre-migration backup is essential:** The migration deletes Client documents. A MongoDB Atlas snapshot or `mongodump` should be taken before running.

## Sources & References

- Related entity: `api/src/main/java/com/nail_art/appointment_book/entities/User.java:21` (unique index precedent)
- Existing cron pattern: `cron/ArchiveAppointments.py`
- Production duplicate data: 10 pairs identified via MongoDB aggregation on 2026-04-07
