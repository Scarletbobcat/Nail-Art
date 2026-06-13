# AGENTS.md

Router for AI agents and contributors working on the Nail Art & Spa appointment book. Keep this file lean — push detail into `docs/`.

## What this repo is

- `client/` — Vite + React + TypeScript + MUI SPA. Served on `5173` in dev, `5173` in the published Docker image.
- `api/` — Spring Boot 3 (Java 21) + PostgreSQL. JWT + refresh-cookie auth. Twilio SMS reminders and appointment archive maintenance run on schedules inside the app.
- `scripts/` — Python admin/bootstrap scripts that operate directly on PostgreSQL.

Read [`docs/INDEX.md`](docs/INDEX.md) for the full map. Architecture lives in [`docs/reference/architecture.md`](docs/reference/architecture.md).

## Common commands

```sh
just dev                # client + api in parallel (5173 + 8080)
just web                # client only
just api                # api only

cd client && npm run lint
cd client && npm test
cd client && npx tsc -b --noEmit  # run before committing FE changes

cd api && ./mvnw test -Dtest="com.nail_art.appointment_book.services.**"
cd api && ./mvnw test -Dtest="PostgresIntegrationSmokeTest"
cd api && ./mvnw spring-boot:run

./start-app.sh          # docker-compose stack from published images
```

## Do

- Read [`docs/reference/conventions.md`](docs/reference/conventions.md) before adding files. Page directories are PascalCase; shared primitives go in `client/src/components/`.
- Read [`docs/reference/lessons.md`](docs/reference/lessons.md) before touching auth, tenancy, search, reminders, or pagination caps. There is real history there.
- Go through the shared `axios` instance (`client/src/api/api.ts`) for HTTP. Per-resource API modules live in `client/src/api/<resource>/`.
- Backend domain rows use UUIDs. Do not add numeric sequence counters.
- Add service-layer tests under `com.nail_art.appointment_book.services` for new backend behavior — CI runs that package.
- Integration tests against real Postgres extend `com.nail_art.appointment_book.PostgresIntegrationTest`; the base manages a shared Testcontainers Postgres 16 container and applied Flyway migrations.
- Every tenant-scoped domain entity carries Hibernate `@TenantId`; web requests populate `TenantContext` through the auth filter, and scheduled jobs use `TenantContext.runAs`.
- Frontend code reads organization timezone from `useMe()` (`orgTz`); do not fall back to the browser timezone for business dates.
- Frontend tests use Vitest; run them with `cd client && npm test`.
- `POST /auth/register` is removed. Seed the owner organization through migrations or admin-only backend operations, not public registration.
- Keep brainstorm / requirements drafts in `docs/brainstorms/` and out of feature commits.

## Don't

- Don't add new `.jsx` files. New components are `.tsx`.
- Don't call `axios` directly from components.
- Don't talk to Twilio outside `SmsService`. Don't route admin-only updates through `editAppointment` — use targeted methods like `markReminderSent` so conflict checks don't fire.
- Don't reintroduce exact-match searches; everything user-facing is partial/case-insensitive.
- Don't loosen CORS or the JWT auth path. Only `/auth/**` is anonymous.
- Don't change the frontend pagination cap without changing the backend cap (currently `2000` on `/clients`).
- Don't remove `client/.npmrc`'s `min-release-age`.

## Where to look next

- Frontend: [`docs/modules/client.md`](docs/modules/client.md)
- Backend: [`docs/modules/api.md`](docs/modules/api.md)
- Local setup: [`docs/reference/local-development.md`](docs/reference/local-development.md)
- Deployment: [`docs/reference/deployment.md`](docs/reference/deployment.md)

## Maintenance & Accretion

Keep this file under 200 lines and as a router only. When a new top-level component, command, or invariant appears, add a one-line entry here and the detail in `docs/`. Record significant docs changes in [`docs/updates.md`](docs/updates.md).

<!-- bv-agent-instructions-v2 -->

---

## Beads Workflow Integration

This project uses [beads_rust](https://github.com/Dicklesworthstone/beads_rust) (`br`) for issue tracking and [beads_viewer](https://github.com/Dicklesworthstone/beads_viewer) (`bv`) for graph-aware triage. Issues are stored in `.beads/` and tracked in git.

### Using bv as an AI sidecar

bv is a graph-aware triage engine for Beads projects (.beads/beads.jsonl). Instead of parsing JSONL or hallucinating graph traversal, use robot flags for deterministic, dependency-aware outputs with precomputed metrics (PageRank, betweenness, critical path, cycles, HITS, eigenvector, k-core).

**Scope boundary:** bv handles *what to work on* (triage, priority, planning). `br` handles creating, modifying, and closing beads.

**CRITICAL: Use ONLY --robot-* flags. Bare bv launches an interactive TUI that blocks your session.**

#### The Workflow: Start With Triage

**`bv --robot-triage` is your single entry point.** It returns everything you need in one call:
- `quick_ref`: at-a-glance counts + top 3 picks
- `recommendations`: ranked actionable items with scores, reasons, unblock info
- `quick_wins`: low-effort high-impact items
- `blockers_to_clear`: items that unblock the most downstream work
- `project_health`: status/type/priority distributions, graph metrics
- `commands`: copy-paste shell commands for next steps

```bash
bv --robot-triage        # THE MEGA-COMMAND: start here
bv --robot-next          # Minimal: just the single top pick + claim command

# Token-optimized output (TOON) for lower LLM context usage:
bv --robot-triage --format toon
```

#### Other bv Commands

| Command | Returns |
|---------|---------|
| `--robot-plan` | Parallel execution tracks with unblocks lists |
| `--robot-priority` | Priority misalignment detection with confidence |
| `--robot-insights` | Full metrics: PageRank, betweenness, HITS, eigenvector, critical path, cycles, k-core |
| `--robot-alerts` | Stale issues, blocking cascades, priority mismatches |
| `--robot-suggest` | Hygiene: duplicates, missing deps, label suggestions, cycle breaks |
| `--robot-diff --diff-since <ref>` | Changes since ref: new/closed/modified issues |
| `--robot-graph [--graph-format=json\|dot\|mermaid]` | Dependency graph export |

#### Scoping & Filtering

```bash
bv --robot-plan --label backend              # Scope to label's subgraph
bv --robot-insights --as-of HEAD~30          # Historical point-in-time
bv --recipe actionable --robot-plan          # Pre-filter: ready to work (no blockers)
bv --recipe high-impact --robot-triage       # Pre-filter: top PageRank scores
```

### br Commands for Issue Management

```bash
br ready              # Show issues ready to work (no blockers)
br list --status=open # All open issues
br show <id>          # Full issue details with dependencies
br create --title="..." --type=task --priority=2
br update <id> --status=in_progress
br close <id> --reason="Completed"
br close <id1> <id2>  # Close multiple issues at once
br sync --flush-only  # Export DB to JSONL
```

### Workflow Pattern

1. **Triage**: Run `bv --robot-triage` to find the highest-impact actionable work
2. **Claim**: Use `br update <id> --status=in_progress`
3. **Work**: Implement the task
4. **Complete**: Use `br close <id>`
5. **Sync**: Always run `br sync --flush-only` at session end

### Key Concepts

- **Dependencies**: Issues can block other issues. `br ready` shows only unblocked work.
- **Priority**: P0=critical, P1=high, P2=medium, P3=low, P4=backlog (use numbers 0-4, not words)
- **Types**: task, bug, feature, epic, chore, docs, question
- **Blocking**: `br dep add <issue> <depends-on>` to add dependencies

### Session Protocol

```bash
git status              # Check what changed
git add <files>         # Stage code changes
br sync --flush-only    # Export beads changes to JSONL
git commit -m "..."     # Commit everything
git push                # Push to remote
```

<!-- end-bv-agent-instructions -->
