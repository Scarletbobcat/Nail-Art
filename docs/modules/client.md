# Module: `client/`

## Purpose

Single-page interface for salon staff: appointment calendar, appointment search, employees, services, clients, and login. Optimized for both desktop and tablet-class mobile use during the workday.

## How it works

Vite + React 18 + TypeScript SPA. The app entrypoint sets up the MUI theme, `CssBaseline`, and a single TanStack Query `QueryClient`, then mounts `App.tsx` which wires routes inside a `BrowserRouter`.

Key files:

- `client/src/main.tsx` — providers (`ThemeProvider`, `CssBaseline`, `QueryClientProvider`).
- `client/src/App.tsx` — routes and the mobile/desktop nav switch.
- `client/theme.ts` — MUI theme palette, typography, shape.
- `client/src/constants/design.ts` — layout tokens (`MOBILE_BREAKPOINT`, `SPACING`, `MAX_CONTENT_WIDTH`, `CALENDAR_COLORS`).
- `client/src/api/api.ts` — shared `axios` instance with the auth interceptor and refresh logic.

### Routing

`App.tsx` defines:

| Path | Component |
| --- | --- |
| `/` → `/Appointments` | redirect |
| `/Appointments` | `AppointmentsPage/Calendar/Calendar.tsx` |
| `/Appointments/Search` | `AppointmentsPage/Search/Search.tsx` |
| `/Clients` | `ClientsPage/Clients.tsx` |
| `/Employees` | `EmployeesPage/Employees.jsx` |
| `/Services` | `ServicesPage/Services.tsx` |
| `/Login` | `Login/Login.tsx` |

The desktop `Navbar` renders on every page; the `MobileBottomNav` only renders below the `MOBILE_BREAKPOINT` (`sm`). `App.tsx` adds bottom padding when mobile to keep content above the nav.

### Data fetching

- All HTTP calls go through `client/src/api/api.ts`'s `axios` instance. Per-resource API modules live in `client/src/api/<resource>/`.
- Components consume the API through TanStack Query hooks (typically `useQuery`/`useMutation`). The single `QueryClient` in `main.tsx` is the cache.
- The response interceptor in `api.ts` handles `401`s by calling `/auth/refresh` once, retrying the original request, and redirecting to `/login` if refresh fails. Components must not implement their own refresh logic.

### State

- Server data: TanStack Query.
- UI state: `useState` / `useReducer` per page.
- Cross-page persistence: `localStorage` (auth `token`, `previousUrl`, calendar `startDate`).
- No global store.

### Responsive design

- `useMediaQuery(theme.breakpoints.down(MOBILE_BREAKPOINT))` decides desktop vs mobile rendering throughout.
- The calendar has separate components: `CustomCalendar` (desktop) and `MobileCalendar` (touch-first, swipeable). They share data but not layout.
- Modals use the shared `components/ResponsiveModal.tsx` which renders as a bottom sheet on mobile and a centered dialog on desktop.

## Interfaces / contracts

- Reads `import.meta.env.VITE_API_URL` at build time. All API requests use that base URL with `withCredentials: true`.
- Reads from / writes to `localStorage`: `token` (access token), `previousUrl` (post-login redirect), `startDate` (calendar selection).
- Reads the HttpOnly `refreshToken` cookie indirectly by hitting `POST /auth/refresh`.
- Backend contract: the controller routes listed in `docs/modules/api.md`.

## Patterns and conventions

- New page → new PascalCase directory at `client/src/<Name>Page/` with `Name.tsx` plus a nested `components/` folder.
- New shared primitive → `client/src/components/<Name>.tsx`.
- Use MUI theme tokens (`theme.palette.*`, `theme.breakpoints.*`) and design tokens from `constants/design.ts`. Avoid hardcoded colors/spacings.
- New TypeScript files. The single remaining `.jsx` file (`EmployeesPage/Employees.jsx`) is grandfathered; do not add new ones.
- React Router v6 future flags are on; write code that is compatible with React Router v7's relative splat path / startTransition behavior.

## Examples and gotchas

- **Verify the build before committing**. `tsc -b` catches type drift across project references; CI runs both `tsc -b --noEmit` and `npm run lint`.
- **Don't replicate auth redirect logic** in pages — `api.ts` already redirects to `/login` and stashes `previousUrl`. Adding more redirect handlers causes loops.
- **Pagination caps must match the backend.** Currently `2000` for clients; bumping it requires changing both ends (see `docs/reference/lessons.md`).
- **Date formatting on the search page uses `timeZone: "UTC"`** to avoid off-by-one display bugs. Keep that on any new component that renders the raw date string.
- **`framer-motion` `Variants` typings** have bitten the build before; if you add motion variants, annotate them and run the build locally.

## Maintenance & Accretion

Update this doc when: a new page or top-level route is added, the auth/token storage scheme changes, the responsive breakpoint policy moves, or the data-fetching library changes. Significant restructures (e.g. introducing a global store) go in `docs/updates.md` with a date.
