# Deployment

## Images

Two prebuilt Docker images are published under the `scarletbobcat/nail-art` namespace and consumed by `docker-compose.yaml`:

| Image | Built from | Runtime | Exposed port |
| --- | --- | --- | --- |
| `scarletbobcat/nail-art:client` | `client/Dockerfile` | `node:23-alpine` serving the Vite build via `serve -s dist -l 5173` | `5173` |
| `scarletbobcat/nail-art:api`    | `api/Dockerfile`    | `bellsoft/liberica-runtime-container:jdk-21-slim-musl` running the Spring Boot fat jar with `SPRING_PROFILES_ACTIVE=prod` | `8080` |

Both images are `linux/arm64` (see `docker-compose.yaml`). Rebuild for `linux/amd64` if you need to deploy on Intel hosts.

## Local bootstrap

`start-app.sh` is the canonical "just run the app" entrypoint:

1. `docker pull -a scarletbobcat/nail-art`
2. `docker-compose up -d`
3. Wait until both `http://localhost:5173` and `http://localhost:8080/employees` respond.
4. `open` the frontend URL.

## Configuration

The API is configured entirely through environment variables (see `docs/reference/local-development.md` for the list). The `prod` profile reads `PROD_MONGO_URI` and `PROD_FRONTEND_URL`.

PostgreSQL migration groundwork is enabled in the API runtime. Set `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` in Render; Spring Boot runs Flyway migrations during API startup and fails the deploy if a migration fails. The request path still uses MongoDB until the repositories are migrated to JPA.

CORS allows exactly one origin (`frontend.url`). Changing the deployed frontend URL requires updating the env var and restarting the API.

## Hosting notes

- The project is wired for [Render](https://render.com)-style deploys. `0efe8ac` pinned `mongodb-driver-sync` to fix SSL handshake failures observed there; keep the pin.
- `api/system.properties` exists for Render's Java runtime detection.
- Refresh tokens require `SameSite=None; Secure`, which means the frontend must be served over HTTPS in production. Don't ship a plain-HTTP deploy.

## CI

`.github/workflows/ci.yml` runs on every push and PR to `main`:

- **Backend Tests**: `./mvnw test -Dtest="com.nail_art.appointment_book.services.**" -q`
- **Frontend Lint & Typecheck**: `npx tsc -b --noEmit` and `npm run lint`

CI does not deploy. Image builds and deploys happen out-of-band.

## Maintenance & Accretion

Update when image tags, exposed ports, runtime base images, or hosting providers change. If a deploy step is added (image build, push, smoke test), document it here.
