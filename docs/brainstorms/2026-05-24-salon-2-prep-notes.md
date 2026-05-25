---
date: 2026-05-24
topic: salon-2-prep-notes
status: future-work
---

# Salon #2 Prep — Notes for the Next Branch

Captured at the tail of the `postgres-migration-cutover` planning session. Not a full brainstorm or plan yet — these are the design decisions and open questions for the *next* branch, the one that lands before (or alongside) onboarding a second salon. Pull this forward as the starting point for `/ce-brainstorm` or `/ce-plan` when the time comes.

## Context

The Postgres cutover branch (`postgres-migration-cutover`, plan at `docs/plans/2026-05-24-001-feat-postgres-cutover-plan.md`) sets up the *foundation* for multi-tenancy but does NOT add the user-facing pieces for a second salon. It deliberately stops short. This doc captures what was deliberately deferred and why, so the future branch doesn't have to rediscover it.

The original brainstorm at `docs/brainstorms/2026-05-24-postgres-cutover-requirements.md` excluded second-salon onboarding from scope. That decision is preserved; this doc is the receipt.

## What's already in place after the cutover branch ships

- **PostgreSQL with the V1 multi-tenant schema.** Every domain table carries `organization_id`; composite FKs prevent cross-tenant references at the schema level.
- **Hibernate `@TenantId` on every domain entity.** Inserts auto-populate `organization_id`; loads auto-add the WHERE clause.
- **Postgres Row Level Security (RLS) on every tenant-scoped table.** The database itself refuses cross-tenant rows even if app code bypasses the JPA layer (native queries, raw JDBC, criteria queries — all covered).
- **Two Postgres roles.** A migration-owner role (used by Flyway) and a runtime app role (no `BYPASSRLS`, can't see other tenants).
- **`TenantContext` ThreadLocal + Spring `OncePerRequestFilter`.** Populates the tenant from the authenticated JWT for HTTP requests.
- **`TaskDecorator` registered on Spring's `TaskScheduler`/`TaskExecutor`.** Async and scheduled jobs inherit the tenant context.
- **`TransactionSynchronization` issuing `SET LOCAL app.org_id = '<uuid>'` per transaction.** This is what RLS reads.
- **`SmsService.sendReminders()` rewritten as a loop-over-orgs pattern.** Today the loop has one iteration (the single salon), with Twilio creds from env vars exactly like today. The loop structure, the per-org `TenantContext.runAs(orgId, ...)` wrapping, and the per-tenant try/catch isolation are all already in place.
- **AE4 cross-tenant isolation test.** Proves the app-layer filter AND the database-layer RLS both refuse cross-tenant rows.

What this means: when the second salon onboards, the data isolation is *already* enforced. The remaining work is about user-facing affordances and configuration, not enforcement.

## What's needed before onboarding a second salon

### 1. Per-tenant Twilio credential storage

Today, `SmsService` reads Twilio `account_sid`, `auth_token`, and `from_number` from env vars. That works for one salon; it doesn't scale (you'd have to redeploy to onboard each new tenant, and the same Render env vars can't hold different credential sets per tenant).

**Schema (V3 Flyway migration in the future branch):**

```
organization_sms_credentials
  organization_id              UUID PK FK organizations(id) ON DELETE CASCADE
  twilio_account_sid           TEXT NOT NULL
  twilio_auth_token_encrypted  BYTEA NOT NULL  -- pgp_sym_encrypt output
  twilio_messaging_service_sid TEXT            -- preferred over from_number
  twilio_from_number           TEXT            -- fallback if no messaging service
  created_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
  CHECK (twilio_messaging_service_sid IS NOT NULL OR twilio_from_number IS NOT NULL)
```

- RLS policy on this table same shape as everywhere else (`USING (organization_id = current_setting('app.org_id')::uuid)`), plus a carve-out for the platform-admin role when that arrives.
- Why separate table (not `organization_settings` extension): cleaner permission boundary. The "owner can edit their own salon's settings" endpoint never touches this table; only platform-admin-gated endpoints do. The structural separation enforces the permission boundary that's otherwise a denylist in the controller.

**Encryption:**
- pgcrypto with an app-held key in env var `SMS_TOKEN_ENC_KEY`
- Encrypted at write via `pgp_sym_encrypt(token, key)`; decrypted in the app via the same key at SMS dispatch time
- Never log the plaintext token; mask in any audit output
- Document a key-rotation procedure (don't build the rotator yet — re-encrypt-with-new-key script is sufficient when needed)

**SmsService refactor:**
- Replace the env-var Twilio client creation with a per-org credential lookup
- Repository: `SmsCredentialsRepository.findByOrganizationId(UUID)` (RLS-scoped — only returns the current tenant's row)
- Service: `SmsCredentialsService.getDecryptedCredentialsForCurrentTenant()` returns the decrypted credentials
- The existing iterate-orgs loop in `sendReminders()` already sets `TenantContext.runAs(orgId, ...)` per iteration; just swap the credential source from env-var to repo lookup

### 2. Onboarding mechanism (Question A from the original brainstorm)

A way for the operator (the developer) to actually create the new salon. Doesn't have to be a UI — a one-shot CLI script is enough.

Reuse the pattern from `migration/migrate_mongo_to_postgres.py`:
- Accept CLI args: `--org-name`, `--org-phone`, `--org-timezone`, `--owner-username`, `--owner-email`, `--owner-password` (prompted via getpass)
- Optional: `--twilio-account-sid`, `--twilio-auth-token` (prompted via getpass), `--twilio-messaging-service-sid` or `--twilio-from-number`
- Insert org row, organization_settings row (with `sms_reminders_enabled` default), owner user row, organization_users row (role=owner), optionally organization_sms_credentials row (with token encrypted via pgcrypto)
- Run as the migration-owner Postgres role (which can write across tenants for setup purposes); the runtime app role never has this capability

Location: `migration/onboard_organization.py` or a new `scripts/` directory — pick at planning time.

### 3. Platform admin (Question B from the original brainstorm — was deferred)

The "log in as any salon for support" capability. Was deferred in the original brainstorm and reconfirmed during this session. Decide at the time of the salon-2-prep branch whether to bundle this in or push it to a separate follow-up.

If bundled:
- `users.is_superuser BOOLEAN DEFAULT false` column, or a separate `platform_admins` table
- JWT carries a `superuser=true` claim when the user is a platform admin; `TenantContext` respects a "bypass mode" that sets `app.org_id` to NULL (which the RLS policies must handle — typically `USING (current_setting('app.org_id', true) IS NULL OR organization_id = current_setting('app.org_id')::uuid)`)
- Audit log table capturing every superuser request (timestamp, user, org being viewed, endpoint, IP)
- UI affordance: persistent "you are impersonating Nail Art & Spa LLC." banner

If deferred further: write a separate prep doc for it; don't try to make the cutover plan grow.

### 4. Org-scoped configuration

Things currently hardcoded that may want to become per-org:

- **SMS reminder time** — currently `@Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")`. If salon #2 is in PT or wants a different hour, this becomes `organization_settings.sms_reminder_local_time TIME` + the scheduler iterates orgs and dispatches when the org-local hour matches.
- **SMS template** — currently hardcoded in `SmsService`. Per-org templating may want to live in `organization_settings.sms_template_text`.
- **Default appointment duration, business hours** — already a known gap; nothing in the cutover plan addresses it.

Probably defer per-org reminder time and template to whenever the second salon actually asks for something different. Don't preempt.

### 5. Tests

- Two-org integration test for the onboarding script (creates two orgs, verifies isolation)
- `SmsService` test with two orgs each having different mocked Twilio creds; assert each org's appointments dispatched through the correct Twilio account
- Cred encryption round-trip test (encrypt + persist + reload + decrypt → same plaintext)
- RLS regression test for the new `organization_sms_credentials` table (auth as org A, can't see org B's row)

## Key decisions already made — don't re-litigate

- **Storage:** separate `organization_sms_credentials` table, not extension of `organization_settings`
- **Encryption:** pgcrypto with app-held key, env var `SMS_TOKEN_ENC_KEY`
- **Scheduler shape:** loop-over-orgs with per-tenant `TenantContext.runAs` and per-tenant try/catch isolation (already implemented in the cutover branch with a loop of size 1; the future branch only changes the credential source and loop size)
- **Multi-tenancy enforcement:** Postgres RLS + Hibernate `@TenantId` (already in place after cutover)
- **Twilio account model assumption:** independent accounts per salon (not subaccounts of a master)

## Open questions for the future branch

- Does the second salon's onboarding require a UI, or is a CLI script enough? (Lean: CLI for now; UI when there are 3+ salons.)
- Should platform admin land in the same branch as Twilio + onboarding, or separately? (Depends on whether you'll need to log in as salon #2 for support before they're self-sufficient.)
- Per-org SMS reminder time / template — needed at salon #2 launch, or defer until they ask?
- Twilio Messaging Service vs from-number per org — pick one as the default new-org pattern.
- Key rotation procedure for `SMS_TOKEN_ENC_KEY` — needs to exist as a documented runbook, even if the rotator script isn't built.

## Pointers

- Active plan that establishes the foundation: `docs/plans/2026-05-24-001-feat-postgres-cutover-plan.md`
- Original requirements brainstorm: `docs/brainstorms/2026-05-24-postgres-cutover-requirements.md`
- Memory note: [[project-second-salon]]
- Lessons (auth invariants, SMS scheduler invariants): `docs/reference/lessons.md`
