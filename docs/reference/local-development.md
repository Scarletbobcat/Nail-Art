# Local Development

## Prerequisites

- **Node.js 20+** for the client (CI pins Node 20).
- **JDK 21** for the API (CI uses Temurin 21).
- **Python 3.14** with [`uv`](https://github.com/astral-sh/uv) if you need the cron scripts.
- **MongoDB Atlas** connection string. There is no local Mongo by default.
- **Docker** if you want the local PostgreSQL container for migration work.
- **Twilio credentials** if you want reminders to actually send. Otherwise leave the env vars empty and skip the scheduled job.

## Environment variables

Create `api/.env` (loaded via `spring.config.import=optional:classpath:.env[.properties]`) with:

```
DEV_MONGO_URI=...
DEV_FRONTEND_URL=http://localhost:5173
PROD_MONGO_URI=...
PROD_FRONTEND_URL=...
JWT_SECRET_KEY=<random base64-encoded secret>
JWT_EXPIRATION=3600000
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE_NUMBER=...
```

Create `client/.env`:

```
VITE_API_URL=http://localhost:8080
```

For cron scripts, create `cron/.env`:

```
MONGO_URI=...
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

The Compose stack includes a local PostgreSQL 16 container for the upcoming persistence migration:

```sh
docker compose up -d postgres
```

The dev profile hardcodes the JDBC URL to `jdbc:postgresql://localhost:5432/nail_art` with username `nail_art` and password `nail_art_password` (see `application-dev.properties`), so no extra env vars are needed in `api/.env`. Spring Boot runs Flyway migrations against this database during API startup. The request path still uses MongoDB until the backend repositories are migrated to JPA.

## Auth bootstrap

There is no public signup UI. Create the first user with:

```sh
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<strong-password>","fullName":"Admin"}'
```

Subsequent logins go through the UI at `http://localhost:5173/Login`.

## Useful checks

- Client typecheck: `cd client && npx tsc -b --noEmit`
- Client lint: `cd client && npm run lint`
- Client build: `cd client && npm run build`
- Backend service tests: `cd api && ./mvnw test -Dtest="com.nail_art.appointment_book.services.**"`

## Docker stack

```sh
./start-app.sh
```

Pulls `scarletbobcat/nail-art:client` and `scarletbobcat/nail-art:api`, brings up `docker-compose.yaml`, waits for both ports to respond, and opens the frontend. Stop with `docker-compose down`.

## Maintenance & Accretion

Update when the env-var contract, port assignments, or required tool versions change. Bumping the minimum Node / JDK / Python version belongs here.
