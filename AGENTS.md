# AGENTS.md

Router for AI agents and contributors working on the Nail Art & Spa appointment book. Keep this file lean — push detail into `docs/`.

## What this repo is

- `client/` — Vite + React + TypeScript + MUI SPA. Served on `5173` in dev, `5173` in the published Docker image.
- `api/` — Spring Boot 3 (Java 21) + MongoDB. JWT + refresh-cookie auth. Twilio SMS reminders on a daily schedule.
- `cron/` — Python 3.14 maintenance scripts (archive, dedupe, sanity checks).

Read [`docs/INDEX.md`](docs/INDEX.md) for the full map. Architecture lives in [`docs/reference/architecture.md`](docs/reference/architecture.md).

## Common commands

```sh
just dev                # client + api in parallel (5173 + 8080)
just web                # client only
just api                # api only

cd client && npm run build     # tsc -b && vite build  (run before committing FE changes)
cd client && npm run lint
cd client && npx tsc -b --noEmit

cd api && ./mvnw test -Dtest="com.nail_art.appointment_book.services.**"
cd api && ./mvnw spring-boot:run

./start-app.sh          # docker-compose stack from published images
```

## Do

- Read [`docs/reference/conventions.md`](docs/reference/conventions.md) before adding files. Page directories are PascalCase; shared primitives go in `client/src/components/`.
- Read [`docs/reference/lessons.md`](docs/reference/lessons.md) before touching auth, counters, search, reminders, or pagination caps. There is real history there.
- Go through the shared `axios` instance (`client/src/api/api.ts`) for HTTP. Per-resource API modules live in `client/src/api/<resource>/`.
- Use `CounterService.getNextSequence("<collection>")` for numeric IDs on the backend. Match the counter name to the collection.
- Add service-layer tests under `com.nail_art.appointment_book.services` for new backend behavior — CI runs that package.
- Run `cd client && npm run build` before committing frontend changes.
- Keep brainstorm / requirements drafts in `docs/brainstorms/` and out of feature commits.

## Don't

- Don't add new `.jsx` files. New components are `.tsx`.
- Don't call `axios` directly from components.
- Don't talk to Twilio outside `SmsService`. Don't route admin-only updates through `editAppointment` — use targeted methods like `markReminderSent` so conflict checks don't fire.
- Don't reintroduce exact-match searches; everything user-facing is partial/case-insensitive.
- Don't loosen CORS or the JWT auth path. Only `/auth/**` is anonymous.
- Don't change the frontend pagination cap without changing the backend cap (currently `2000` on `/clients`).
- Don't remove `mongodb-driver-sync` version pin in `api/pom.xml` or `client/.npmrc`'s `min-release-age`.

## Where to look next

- Frontend: [`docs/modules/client.md`](docs/modules/client.md)
- Backend: [`docs/modules/api.md`](docs/modules/api.md)
- Cron jobs: [`docs/modules/cron.md`](docs/modules/cron.md)
- Local setup: [`docs/reference/local-development.md`](docs/reference/local-development.md)
- Deployment: [`docs/reference/deployment.md`](docs/reference/deployment.md)

## Maintenance & Accretion

Keep this file under 200 lines and as a router only. When a new top-level component, command, or invariant appears, add a one-line entry here and the detail in `docs/`. Record significant docs changes in [`docs/updates.md`](docs/updates.md).
