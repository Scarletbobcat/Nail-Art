# Local Development

## Prerequisites

- **Node.js 20+** for the client (CI pins Node 20).
- **JDK 21** for the API (CI uses Temurin 21).
- **Python 3.14** with [`uv`](https://github.com/astral-sh/uv) if you need the cron or bootstrap scripts.
- **Docker** for the local PostgreSQL container.
- **Twilio credentials** if you want reminders to actually send. Otherwise leave the env vars empty and skip the scheduled job.

## Environment variables

Create `api/src/main/resources/.env` or export equivalent shell env vars for local API work:

```
DEV_FRONTEND_URL=http://localhost:5173
PROD_FRONTEND_URL=
JWT_SECRET_KEY=<random base64-encoded secret>
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=2592000000
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
```

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

For cron and bootstrap scripts, create `cron/.env` or export:

```
POSTGRES_URL=postgresql://nail_art:nail_art_password@localhost:5432/nail_art
```

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
