# Deployment

## Images

Two prebuilt Docker images are published under the `scarletbobcat/nail-art` namespace and consumed by `docker-compose.yaml`:

| Image | Built from | Runtime | Exposed port |
| --- | --- | --- | --- |
| `scarletbobcat/nail-art:client` | `client/Dockerfile` | `node:23-alpine` serving the Vite build via `serve -s dist -l 5173` | `5173` |
| `scarletbobcat/nail-art:api` | `api/Dockerfile` | `bellsoft/liberica-runtime-container:jdk-21-slim-musl` running the Spring Boot fat jar with `SPRING_PROFILES_ACTIVE=prod` | `8080` |

Both images are `linux/arm64` (see `docker-compose.yaml`). Rebuild for `linux/amd64` if you need to deploy on Intel hosts.

## Local bootstrap

`start-app.sh` is the canonical "just run the app" entrypoint:

1. `docker pull -a scarletbobcat/nail-art`
2. `docker-compose up -d`
3. Wait until both `http://localhost:5173` and `http://localhost:8080/employees` respond.
4. `open` the frontend URL.

The Compose stack starts PostgreSQL before the API and wires `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` into the API container.

## Configuration

The API is configured through environment variables (see `docs/reference/local-development.md` for the local list). Production must provide:

- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `PROD_FRONTEND_URL`
- `JWT_SECRET_KEY`
- `JWT_EXPIRATION`
- `JWT_REFRESH_EXPIRATION`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_PHONE_NUMBER`

Spring Boot runs Flyway migrations during API startup and fails the deploy if a migration fails.

CORS allows exactly one origin (`frontend.url`). Changing the deployed frontend URL requires updating `PROD_FRONTEND_URL` and restarting the API.

## Hosting notes

- The project is wired for [Render](https://render.com)-style deploys. Render should provide the production PostgreSQL connection details through env vars.
- `api/system.properties` exists for Render's Java runtime detection.
- Refresh tokens require `SameSite=None; Secure`, which means the frontend must be served over HTTPS in production. Don't ship a plain-HTTP deploy.
- Historical cutover note: Mongo remains the read-only rollback source until the production migration is verified; the running API image after merge is PostgreSQL-only.

## CI

`.github/workflows/ci.yml` runs on every push and PR to `main`:

- **Backend Tests**: `./mvnw test -Dtest="com.nail_art.appointment_book.services.**" -q`
- **Frontend Lint & Typecheck**: `npx tsc -b --noEmit` and `npm run lint`

CI does not deploy. Image builds and deploys happen out-of-band.

## Maintenance & Accretion

Update when image tags, exposed ports, runtime base images, hosting providers, or runtime env vars change. If a deploy step is added (image build, push, smoke test), document it here.
