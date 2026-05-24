# Module: `api/`

## Purpose

Spring Boot REST API for the appointment book. Handles CRUD over appointments, clients, employees, services, and users; issues and validates JWTs; sends Twilio SMS reminders on a schedule.

## How it works

Spring Boot 3.3 on Java 21, packaged as a fat jar by the Maven wrapper (`./mvnw`). Persistence is MongoDB through Spring Data. Security is JWT-based with a long-lived refresh-token cookie.

Top-level package: `com.nail_art.appointment_book` with these subpackages:

- `controllers/` — REST endpoints, one per resource.
- `services/` — business logic.
- `repositories/` — `MongoRepository` interfaces.
- `entities/` — Mongo documents (`@Document`).
- `dtos/` — request/response shapes that diverge from entities (`LoginUserDto`, `RegisterUserDto`, `TokenDto`).
- `configs/` — Spring beans (security, JWT filter, application config, Mongo index verification).
- `exceptions/` — `GlobalExceptionHandler` and friends.
- `responses/` — `LoginResponse` and other small response wrappers.

## Endpoints

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | none | Bootstraps users. |
| `POST` | `/auth/login` | none | Returns access token + sets `refreshToken` cookie. |
| `POST` | `/auth/refresh` | none (cookie) | Rotates access token. |
| `POST` | `/auth/logout` | none (cookie) | Revokes server-side refresh token, clears cookie. |
| `GET` | `/users/me` | JWT | Current user. |
| `GET` | `/users/` | JWT | All users. |
| `GET` | `/appointments/` | JWT | All appointments. |
| `GET` | `/appointments/{id}` | JWT | By numeric id. |
| `GET` | `/appointments/date/{date}` | JWT | All appointments for a date (`YYYY-MM-DD`). |
| `GET` | `/appointments/search/{phoneNumber}` | JWT | Partial, case-insensitive phone match. |
| `POST` | `/appointments/create` | JWT | Validates body; runs per-employee time-slot conflict check. |
| `PUT` | `/appointments/edit` | JWT | Validates and conflict-checks; relinks client by phone if `clientId` is missing. |
| `DELETE` | `/appointments/delete` | JWT | Returns 404 if not found. |
| `GET` | `/clients/` | JWT | Paginated search (`name`, `phoneNumber` regex). |
| `GET` | `/clients/{id}` | JWT | By numeric id. |
| `POST` | `/clients/create` | JWT | Enforces unique `phoneNumber`. |
| `PUT` | `/clients/edit` | JWT | |
| `DELETE` | `/clients/delete` | JWT | |
| `GET` | `/employees/` | JWT | Paginated. |
| `GET` | `/employees/name/{name}` | JWT | `ContainingIgnoreCase`. |
| `POST` | `/employees/create` | JWT | |
| `PUT` | `/employees/edit` | JWT | |
| `DELETE` | `/employees/delete` | JWT | |
| `GET` | `/services/` | JWT | Paginated. |
| `GET` | `/services/name/{name}` | JWT | `ContainingIgnoreCase`. |
| `POST` | `/services/create` | JWT | |
| `PUT` | `/services/edit` | JWT | |
| `DELETE` | `/services/delete` | JWT | |

Validation errors return `400` with `{ field: message }` (collected from `BindingResult`). `DuplicateKeyException` maps to `409`, `IllegalArgumentException` to `400`.

## Persistence

Collections (and their entities): `Appointments` (`Appointment`), `ArchivedAppointments` (used by `cron/`), `Clients` (`Client`), `Employees` (`Employee`), `Services` (`Service`), `Users` (`User`), `RefreshToken`, and a `Counter` document used as a numeric sequence generator.

`spring.data.mongodb.auto-index-creation=true` is set, so entity index annotations are applied at startup. `MongoConfig` additionally verifies the `Clients.phoneNumber` unique partial index on boot.

## Authentication

- `SecurityConfiguration` permits only `/auth/**` anonymously; everything else requires authentication via `JwtAuthenticationFilter`.
- `JwtService` issues access tokens (short-lived, `JWT_EXPIRATION`) and refresh tokens (30 days). Refresh tokens are also stored in the `RefreshToken` collection so logout can revoke them.
- Access tokens travel as `Authorization: Bearer …`. Refresh tokens travel as the `refreshToken` cookie (`HttpOnly`, `SameSite=None`, `Secure`).
- CORS is locked to `frontend.url`; `Set-Cookie` is exposed so the SPA can complete the refresh roundtrip.
- Username lookup is `findByUsernameIgnoreCase` end-to-end.

## SMS reminders

`SmsService` is the single Twilio integration point:

- `@Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")` — fires daily at 3 PM ET.
- Loads tomorrow's appointments via `AppointmentService#getAppointmentsForTomorrow`, skips ones already marked `reminderSent` or missing a phone number, and sends a templated message.
- Retries on `5xx`/`429` up to `MAX_ATTEMPTS=3` with a 5-second backoff.
- Treats Twilio error code `21610` (unsubscribed) as a terminal skip.
- Marks success with `AppointmentService#markReminderSent` — a targeted update that does **not** re-run conflict checks. See `docs/reference/lessons.md`.

## Configuration

Properties files under `api/src/main/resources/`:

- `application.properties` — common (app name, Mongo auto-index, JWT and Twilio env-var wiring).
- `application-dev.properties` — `DEV_MONGO_URI`, `DEV_FRONTEND_URL`.
- `application-prod.properties` — `PROD_MONGO_URI`, `PROD_FRONTEND_URL`.

Secrets come from env vars (`JWT_SECRET_KEY`, `JWT_EXPIRATION`, `TWILIO_*`). The active profile defaults to `dev`; production images set `SPRING_PROFILES_ACTIVE=prod`.

## Patterns and conventions

- One controller per resource, base path `/<resource>`; CRUD operations on `/create`, `/edit`, `/delete` plus REST-ish lookups.
- Request body is the entity itself with Lombok `@Data`. For login/register use the DTOs.
- Numeric IDs go through `CounterService#getNextSequence("<collection>")` — never invent IDs in callers, and never reuse a counter name across collections.
- New service-layer behavior gets a unit test under `com.nail_art.appointment_book.services` (CI runs this package).
- Don't talk to Twilio outside `SmsService`. Don't route admin-only field updates through `editAppointment`.

## Examples and gotchas

- **Pin the MongoDB driver.** `mongodb-driver-sync` is set to `${mongodb.version}` to avoid SSL handshake failures on Render (`0efe8ac`). Don't drop the pin.
- **Refresh token cookie has a 30-day `maxAge`.** Removing it logs everyone out on browser close.
- **Don't loosen CORS.** Only the configured `frontend.url` is allowed, and `allowCredentials=true`.
- **Search uses `Containing` / regex.** Don't reintroduce exact-match searches; client- and employee-facing flows depend on partial matching.

## Maintenance & Accretion

Update when: a controller is added/removed, the auth model changes, a scheduled job moves, or property/env wiring changes. Add service tests for new service behavior; CI will exercise them. Record cross-cutting changes in `docs/updates.md`.
