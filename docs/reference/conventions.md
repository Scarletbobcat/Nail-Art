# Conventions

These are conventions observed in the existing code. Match them when extending the project; deviating creates avoidable churn.

## Repository layout

- `client/` — frontend SPA. Source under `client/src`, theme in `client/theme.ts`, design tokens in `client/src/constants/design.ts`.
- `api/` — Spring Boot service. Java sources under `api/src/main/java/com/nail_art/appointment_book`.
- `cron/` — Python maintenance scripts. Managed with `uv` / `pyproject.toml`.
- `docs/` — canonical documentation suite. Brainstorm/working notes live in `docs/brainstorms/` and are not committed with feature work.
- `images/` — README screenshots, kebab-case PNGs.

## Frontend (`client/`)

- **Language**: TypeScript. New components are `.tsx`. Legacy `.jsx` exists (e.g. `EmployeesPage/Employees.jsx` is imported as `.jsx`) and is being migrated incrementally — do not introduce new `.jsx` files.
- **Page directories**: Each top-level page lives in its own PascalCase directory (`AppointmentsPage/`, `ClientsPage/`, `EmployeesPage/`, `ServicesPage/`, `Login/`, `Navbar/`). Page-internal components nest under `<Page>/components/` (or `<Page>/<Feature>/components/`).
- **Shared components**: Cross-page primitives live in `client/src/components/` (`Alert`, `Button`, `CardList`, `CircularLoading`, `PageHeader`, `PageSkeleton`, `ResponsiveModal`, `AnimatedPage`).
- **State & data**:
  - Server state goes through TanStack Query. The single `QueryClient` is created in `client/src/main.tsx`.
  - API calls live under `client/src/api/<resource>/` and import the shared `axios` instance from `client/src/api/api.ts`. Do not call `axios` directly from components.
  - Local UI state uses `useState` / `useReducer`. There is no global store.
- **Auth tokens**: Access token stored in `localStorage` under `token`. Redirects on auth failure are handled centrally in the `api.ts` response interceptor — do not duplicate this logic in pages.
- **Routing**: `react-router-dom` v6 with future flags `v7_startTransition` and `v7_relativeSplatPath` enabled. Top-level routes are defined in `client/src/App.tsx`. The default path redirects to `/Appointments`.
- **Theming & responsive**:
  - All MUI theming comes from `client/theme.ts`. Use the theme palette and `borderRadius: 12`; avoid hardcoded colors.
  - Layout constants live in `client/src/constants/design.ts` (`MOBILE_BREAKPOINT`, `SPACING`, `MAX_CONTENT_WIDTH`, `CALENDAR_COLORS`). Reuse them rather than re-declaring numbers.
  - Mobile detection: `useMediaQuery(theme.breakpoints.down(MOBILE_BREAKPOINT))`. The bottom nav (`MobileBottomNav`) only renders on mobile widths.
- **Build**: `npm run build` runs `tsc -b && vite build`. The build must succeed before committing frontend changes.

## Backend (`api/`)

- **Package layout**: All code lives under `com.nail_art.appointment_book` split into `controllers`, `services`, `repositories`, `entities`, `dtos`, `configs`, `exceptions`, `responses`.
- **Routing**: Controllers expose a base `@RequestMapping("/<resource>")` (e.g. `/appointments`, `/clients`, `/employees`, `/services`, `/users`, `/auth`). Operation paths use lower-kebab segments: `/create`, `/edit`, `/delete`, plus REST-ish `/{id}`, `/date/{date}`, `/search/{phoneNumber}`.
- **HTTP verbs**: `GET` for reads, `POST /create`, `PUT /edit`, `DELETE /delete`. Request and response bodies are entity classes (Lombok `@Data`); validation uses `@Valid` + `BindingResult` and returns a `Map<String,String>` of field errors on `400`.
- **Persistence**:
  - Entities are annotated with `@Document(collection = "<PascalCase>")` and use a `String _id` Mongo identifier plus a numeric `id` sequence sourced from `CounterService`.
  - Repositories extend `MongoRepository` and live in `repositories/`.
- **Auth**:
  - JWT bearer tokens + HttpOnly refresh cookie. Only `/auth/**` is permitted anonymously; everything else requires authentication.
  - CORS is locked to the configured `frontend.url` and exposes `Set-Cookie`.
- **Twilio / scheduled**: `SmsService` is the only place that talks to Twilio. It encapsulates retries (`MAX_ATTEMPTS=3`, 5s backoff), unsubscribed handling (code `21610`), masked phone logging, and `@Scheduled` cron timing.
- **Configuration**: Profile-specific properties live in `application-dev.properties` / `application-prod.properties`. Secrets are read from environment variables — never hard-code them.
- **Tests**: Service tests under `com.nail_art.appointment_book.services.**` are run in CI. Add new service tests under the same package root.

## Cron (`cron/`)

- Python 3.14, managed with `uv` (`uv.lock` is committed). Dependencies are declared in `pyproject.toml`.
- Scripts read `MONGO_URI` from a `.env` via `python-dotenv` and talk to MongoDB with `pymongo`.
- One responsibility per script. Keep them idempotent and safe to rerun.

## Documentation

- Root files (`README.md`, `AGENTS.md`, `CLAUDE.md`) stay lean and link into `docs/`.
- Every canonical doc under `docs/` ends with a `Maintenance & Accretion` section that says when it should be updated.
- Significant documentation changes are appended to `docs/updates.md` with a date and one-liner.
- Brainstorm and requirements drafts live in `docs/brainstorms/` and are kept out of feature commits.

## Maintenance & Accretion

Update this document when an established convention changes (new directory layout, new state library, replaced routing/auth mechanism, new lint or formatting rules). If a convention is in flux, write `I assume …` and link to a tracking note in `docs/updates.md`.
