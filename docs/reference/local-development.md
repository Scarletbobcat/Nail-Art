# Local Development

## Prerequisites

- **Node.js 20+** for the client (CI pins Node 20).
- **JDK 21** for the API (CI uses Temurin 21).
- **Python 3.14** with [`uv`](https://github.com/astral-sh/uv) if you need the admin/bootstrap scripts.
- **Docker** for the local PostgreSQL container.
- **Twilio credentials** if you want reminders to actually send. These now live per-organization in the database (encrypted), not in env vars — set them via the owner Settings page or `scripts/set_org_twilio.py`. Without any configured org, the scheduled job simply skips every org.

## Environment variables

Create `api/src/main/resources/.env` or export equivalent shell env vars for local API work:

```
DEV_FRONTEND_URL=http://localhost:5173
PROD_FRONTEND_URL=
JWT_SECRET_KEY=<random base64-encoded secret>
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=2592000000
APP_ENCRYPTION_KEY=<random secret; pgcrypto passphrase for per-org Twilio tokens>
```

`APP_ENCRYPTION_KEY` has no default — the API fails to start without it. Use the
same value for the Python scripts (it decrypts the same tokens), and keep it
stable: rotating it strands every token already encrypted under the old key.
Generate one with `openssl rand -base64 48`.

Per-org Twilio credentials (account SID, auth token, phone number) are stored on
`organization_settings`, with the auth token encrypted via pgcrypto. To load an
org's credentials, run `scripts/set_org_twilio.py` (token read from
`TWILIO_AUTH_TOKEN` env or an interactive prompt — never a CLI argument; run with
`HISTFILE=/dev/null`), or use the owner Settings page.

The `dev` profile points at the local Compose database:

```
POSTGRES_URL=jdbc:postgresql://localhost:5432/nail_art
POSTGRES_USER=nail_art
POSTGRES_PASSWORD=nail_art_password
```

Create `client/.env`:

```
VITE_API_URL=http://localhost:8080
```

For Python admin/bootstrap scripts, create `scripts/.env` or export:

```
POSTGRES_URL=postgresql://nail_art:nail_art_password@localhost:5432/nail_art
APP_ENCRYPTION_KEY=<same value as the API; decrypts per-org Twilio tokens>
```

`scripts/send_reminders.py` needs `APP_ENCRYPTION_KEY` to decrypt tokens when
sending; `scripts/set_org_twilio.py` needs it to write them. The cutover script
also reads `TWILIO_ACCOUNT_SID` / `TWILIO_PHONE_NUMBER` (non-secret) and the auth
token from `TWILIO_AUTH_TOKEN` or an interactive prompt.

For Docker Compose, copy the root `.env.example` to `.env` if you want to override the local PostgreSQL defaults:

```
POSTGRES_PORT=5432
POSTGRES_DB=nail_art
POSTGRES_USER=nail_art
POSTGRES_PASSWORD=nail_art_password
```

## Run both services

The repo ships a `justfile`:

```sh
just web         # cd client && npm run dev   (port 5173, --host)
just api         # cd api && ./mvnw spring-boot:run   (port 8080)
just dev         # web + api in parallel
just web-install # cd client && npm install
```

Without `just`, run the two commands directly in separate terminals.

The default Spring profile is `dev`. To run against prod settings locally, set `SPRING_PROFILES_ACTIVE=prod` before starting the API.

## Local PostgreSQL

Start PostgreSQL 16:

```sh
docker compose up -d postgres
```

Spring Boot runs Flyway migrations against this database during API startup. The local defaults match `application-dev.properties`.

## Auth bootstrap

There is no public signup route. For a fresh local database:

```sh
python scripts/create_organization.py \
  --db-url postgresql://nail_art:nail_art_password@localhost:5432/nail_art \
  --name "Nail Art & Spa"

python scripts/bootstrap_organization_owner.py \
  --db-url postgresql://nail_art:nail_art_password@localhost:5432/nail_art \
  --org-name "Nail Art & Spa" \
  --username admin \
  --password "<strong-password>"
```

Subsequent logins go through the UI at `http://localhost:5173/Login`.

## Useful checks

- Client typecheck: `cd client && npx tsc -b --noEmit`
- Client lint: `cd client && npm run lint`
- Backend service tests: `cd api && ./mvnw test -Dtest="com.nail_art.appointment_book.services.**"`
- PostgreSQL smoke test: `cd api && ./mvnw test -Dtest=PostgresIntegrationSmokeTest`

## Docker stack

```sh
./start-app.sh
```

Pulls `scarletbobcat/nail-art:client` and `scarletbobcat/nail-art:api`, brings up `docker-compose.yaml`, waits for both ports to respond, and opens the frontend. Stop with `docker-compose down`.

## Maintenance & Accretion

Update when the env-var contract, port assignments, or required tool versions change. Bumping the minimum Node / JDK / Python version belongs here.
