# Conventions

These are conventions observed in the existing code. Match them when extending the project; deviating creates avoidable churn.

## Repository layout

- `client/` — frontend SPA. Source under `client/src`, theme in `client/theme.ts`, design tokens in `client/src/constants/design.ts`.
- `api/` — Spring Boot service. Java sources under `api/src/main/java/com/nail_art/appointment_book`.
- `cron/` — Python maintenance scripts. Managed with `uv` / `pyproject.toml`.
- `docs/` — canonical documentation suite. Brainstorm/working notes live in `docs/brainstorms/` and are not committed with feature work.
- `images/` — README screenshots, kebab-case PNGs.

## Frontend (`client/`)

- **Language**: TypeScript. New components are `.tsx`. Legacy `.jsx` exists (`EmployeesPage/Employees.jsx`) and is being migrated incrementally; do not introduce new `.jsx` files.
- **Page directories**: Each top-level page lives in its own PascalCase directory (`AppointmentsPage/`, `ClientsPage/`, `EmployeesPage/`, `ServicesPage/`, `Login/`, `Navbar/`). Page-internal components nest under `<Page>/components/` or `<Page>/<Feature>/components/`.
- **Shared components**: Cross-page primitives live in `client/src/components/`.
- **State & data**:
  - Server state goes through TanStack Query. The single `QueryClient` is created in `client/src/main.tsx`.
  - API calls live under `client/src/api/<resource>/` and import the shared `axios` instance from `client/src/api/api.ts`. Do not call `axios` directly from components.
  - Current user/org data comes from `client/src/hooks/useMe.ts`.
  - Local UI state uses `useState` / `useReducer`. There is no global store.
- **Auth tokens**: Access token is stored in `localStorage` under `token`. Redirects on auth failure are handled centrally in the `api.ts` response interceptor.
- **Routing**: `react-router-dom` v6 with future flags `v7_startTransition` and `v7_relativeSplatPath` enabled. Top-level routes are defined in `client/src/App.tsx`. The default path redirects to `/Appointments`.
- **Theming & responsive**:
  - All MUI theming comes from `client/theme.ts`. Use the theme palette and design tokens; avoid hardcoded colors.
  - Layout constants live in `client/src/constants/design.ts`.
  - Shared non-theme semantic colors live in `client/src/utils/colors.ts`.
  - Mobile detection: `useMediaQuery(theme.breakpoints.down(MOBILE_BREAKPOINT))`.
- **Appointment time handling**: Use `client/src/utils/datetime.ts`; format appointment timestamps with `.tz(orgTz)` from `useMe()`. Do not fall back to browser timezone for business dates.
- **Verification**: Run `cd client && npx tsc -b --noEmit` before committing frontend changes. Run lint when touching linted code.

## Backend (`api/`)

- **Package layout**: All code lives under `com.nail_art.appointment_book` split into `controllers`, `services`, `repositories`, `entities`, `dtos`, `configs`, `exceptions`, `responses`, and `multitenancy`.
- **Routing**: Controllers expose a base `@RequestMapping("/<resource>")` (e.g. `/appointments`, `/clients`, `/employees`, `/services`, `/users`, `/auth`). Operation paths keep the existing `/create`, `/edit`, `/delete` shape plus REST-ish lookups like `/{id}`, `/date/{date}`, and `/search/{phoneNumber}`.
- **HTTP verbs**: `GET` for reads, `POST /create`, `PUT /edit/{id}`, `DELETE /delete/{id}`. Request and response bodies are usually entity classes (Lombok `@Data` or explicit accessors); auth and user creation use DTOs.
- **IDs**: Public IDs are UUID strings. New rows rely on PostgreSQL defaults; do not add application-managed numeric sequence counters.
- **Persistence**:
  - Entities are JPA entities backed by Flyway-managed PostgreSQL tables.
  - Repositories extend `JpaRepository`.
  - Tenant-owned entities use Hibernate `@TenantId`; repository lookups that cross an ID boundary should use explicit scoped methods such as `findScopedById(UUID)`.
  - Client-supplied organization IDs are ignored. Web requests get tenant scope from the authenticated principal; scheduled jobs use `TenantContext.runAs`.
- **Auth**:
  - JWT bearer tokens + HttpOnly refresh cookie. Only `/auth/**` is permitted anonymously.
  - JWTs carry user UUID (`sub`), organization UUID (`org`), and role.
  - `JwtAuthenticationFilter` cross-checks token membership against `organization_users`.
  - CORS is locked to the configured `frontend.url` and exposes `Set-Cookie`.
- **Twilio / scheduled**: `SmsService` is the only place that talks to Twilio. It encapsulates retries, unsubscribed handling (code `21610`), masked phone logging, `@Scheduled` cron timing, and per-org `TenantContext.runAs`.
- **Configuration**: Profile-specific properties live in `application-dev.properties` / `application-prod.properties`. Secrets and connection strings are read from environment variables; never hard-code them.
- **Tests**: Service tests under `com.nail_art.appointment_book.services.**` are run in CI. PostgreSQL integration tests extend `PostgresIntegrationTest`.

## Cron (`cron/`)

- Python 3.14, managed with `uv` (`uv.lock` is committed). Dependencies are declared in `pyproject.toml`.
- Scripts read `POSTGRES_URL` from a `.env` via `python-dotenv` and talk to PostgreSQL with `psycopg`.
- One responsibility per script. Keep scripts idempotent and safe to rerun.
- Multi-tenant scripts loop organizations explicitly; do not introduce a single-org environment variable for tenant selection.

## Documentation

- Root files (`README.md`, `AGENTS.md`, `CLAUDE.md`) stay lean and link into `docs/`.
- Every canonical doc under `docs/` ends with a `Maintenance & Accretion` section that says when it should be updated.
- Significant documentation changes are appended to `docs/updates.md` with a date and one-liner.
- Brainstorm and requirements drafts live in `docs/brainstorms/` and are kept out of feature commits.

## Maintenance & Accretion

Update this document when an established convention changes (new directory layout, new state library, replaced routing/auth/persistence mechanism, new lint or formatting rules). If a convention is in flux, write `I assume ...` and link to a tracking note in `docs/updates.md`.
