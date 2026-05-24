# Architecture

## System shape

Three independently deployable pieces sit behind a single business workflow.

```
+----------------+        HTTPS / JWT         +----------------------+        +-------------------+
|  client/       |  -----------------------> |  api/                |  --->  |  MongoDB Atlas    |
|  Vite + React  |                            |  Spring Boot (Java)  |        |  "Nail-Art" db    |
|  MUI, TS       |  <- refreshToken cookie -- |                      |        +-------------------+
+----------------+                            +----------------------+
                                                       |
                                                       | Twilio REST (scheduled)
                                                       v
                                              +----------------------+
                                              |  Twilio              |
                                              |  SMS reminders       |
                                              +----------------------+

+------------------+        +-------------------+
|  cron/           |  --->  |  MongoDB Atlas    |
|  Python scripts  |        |  archive cleanup  |
+------------------+        +-------------------+
```

## Components

- **`client/`** — Vite + React 18 + TypeScript single-page app. Uses Material UI (`@mui/material`, `@mui/x-data-grid`, `@mui/x-date-pickers`), TanStack Query for server state, and `react-router-dom` v6 for routing. Bundles to `client/dist/` and is served via `serve` in production.
- **`api/`** — Spring Boot 3.3 (Java 21) REST API. Spring Security with JWT access tokens (`Authorization: Bearer …`) and an HttpOnly `refreshToken` cookie. Persists to MongoDB through Spring Data MongoDB. Sends Twilio SMS reminders on a scheduled cron.
- **`cron/`** — Python 3.14 maintenance scripts that talk directly to MongoDB for archive sweeping and ad‑hoc data hygiene. Not part of the request path.

## Request flow (typical authenticated call)

1. User loads the SPA from `client/dist` (served on port `5173`).
2. `client/src/api/api.ts` creates an `axios` instance with `withCredentials: true`. It attaches `Authorization: Bearer <token>` from `localStorage`.
3. On `401`, the response interceptor calls `POST /auth/refresh` (using the HttpOnly refresh cookie), swaps in the new access token, and retries the original request. If refresh fails, the user is redirected to `/login`.
4. `api/` validates the JWT through `JwtAuthenticationFilter` and routes through controllers under `com.nail_art.appointment_book.controllers` to services and Spring Data repositories.
5. CORS in `SecurityConfiguration` allows only the configured `frontend.url` and exposes `Set-Cookie` so the refresh cookie can roundtrip.

## Authentication model

- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` are the only unauthenticated routes.
- Access token lives in `localStorage` under `token`. Refresh token is an HttpOnly, `SameSite=None`, `Secure` cookie with a 30‑day TTL. Both are issued by `JwtService`.
- Refresh tokens are tracked server-side via the `RefreshToken` entity so logout can revoke them.

## Persistence

- MongoDB collections: `Appointments`, `ArchivedAppointments`, `Clients`, `Employees`, `Services`, `Users`, `RefreshToken`, plus a `Counter` document used as a sequence generator for numeric IDs.
- Numeric `id` fields (e.g. `Appointment.id`, `Client.id`) are app-managed sequences distinct from Mongo's `_id`. `CounterService` increments them.
- `spring.data.mongodb.auto-index-creation=true` so index annotations on entities are applied at startup.

## Scheduled work

- `SmsService#sendReminders` runs at `0 0 15 * * *` America/New_York. It loads tomorrow's appointments, sends Twilio messages with retry/backoff (`MAX_ATTEMPTS=3`, 5s backoff), respects Twilio code `21610` (unsubscribed), and marks `reminderSent=true` on success.
- `cron/ArchiveAppointments.py` is intended to run weekly. It moves appointments older than 14 days into `ArchivedAppointments` and purges archives older than 30 days.

## Configuration & secrets

API reads environment-driven properties (see `api/src/main/resources/application*.properties`):

- `JWT_SECRET_KEY`, `JWT_EXPIRATION`
- `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER`
- `DEV_MONGO_URI` / `PROD_MONGO_URI`
- `DEV_FRONTEND_URL` / `PROD_FRONTEND_URL`

Client reads `VITE_API_URL` at build/runtime via `import.meta.env`.

The active Spring profile is `dev` by default and `prod` in the Docker images.

## Deployment

- Published Docker images: `scarletbobcat/nail-art:client` (serves the Vite build via `serve` on `5173`) and `scarletbobcat/nail-art:api` (runs the Spring Boot fat jar on `8080`). `docker-compose.yaml` wires them together.
- `start-app.sh` pulls the latest images, brings the stack up, waits for both `5173` and `8080` to respond, and opens the browser.
- CI (`.github/workflows`) runs backend tests (`./mvnw test -Dtest="com.nail_art.appointment_book.services.**"`) and frontend `tsc -b --noEmit` + `npm run lint` on every push and PR to `main`.

## Maintenance & Accretion

Update this document when: a new top-level component is added or retired, the auth model changes, scheduled jobs are added/moved, or the deployment topology shifts. Record changes briefly in `docs/updates.md`.
