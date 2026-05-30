# Salon Onboarding & Twilio Runbook

How to onboard a new salon and how to load per-organization Twilio credentials.
Twilio credentials live encrypted in the database per organization (pgcrypto),
not in env vars — they are operator-managed via `scripts/set_org_twilio.py`, not
the owner Settings page.

All commands run from `scripts/` with `uv`. The three scripts read `scripts/.env`
automatically (`POSTGRES_URL`, `APP_ENCRYPTION_KEY`, and any `TWILIO_*` values).

## Prerequisites

- `scripts/.env` contains `POSTGRES_URL` for the target database and
  `APP_ENCRYPTION_KEY` set to **the exact same value** as the API's
  `APP_ENCRYPTION_KEY` (Render dashboard for prod). A mismatched key means tokens
  written here cannot be decrypted at send time — the send path fails loudly.
- Run against prod from a host the Render database accepts (your machine), not a
  sandbox.
- `APP_ENCRYPTION_KEY` must never change once tokens exist — rotating it strands
  every previously-encrypted token (no rotation tooling, by design).

## Scripts

| Script | Purpose |
|--------|---------|
| `create_organization.py` | Create an org + its `organization_settings` row + the "Unavailable" marker service. |
| `bootstrap_organization_owner.py` | Create the first owner login for an org. |
| `set_org_twilio.py` | Load an org's Twilio credentials into its DB row (auth token encrypted). |

## A. Cut over the existing salon to DB Twilio

The existing salon already has `sms_reminders_enabled = true` and its Twilio
credentials in `scripts/.env`. This moves those into its DB row, encrypted.

```sh
cd scripts

# 1. Find the org id/name if needed:
uv run python -c "import os,psycopg; from dotenv import load_dotenv; load_dotenv(); \
u=os.environ['POSTGRES_URL'].removeprefix('jdbc:'); \
print(psycopg.connect(u).execute('select id, name from organizations').fetchall())"

# 2. Load creds into the DB. SID / phone / token are read from .env;
#    HISTFILE=/dev/null keeps the token out of shell history.
HISTFILE=/dev/null uv run set_org_twilio.py --org-name "<existing org name>"
```

**Verify:** sign in as that salon's owner → Settings → the SMS toggle is now live
(no longer locked) and still on. Reminders resume at the next 15:00 ET cron.

**Timing:** deploying this change retires the env-based send path, so reminders
skip until the DB row is populated. Deploy in a quiet window, then run this
cutover **before the next 15:00 ET send**.

## B. Onboard a new salon

Each salon brings its own Twilio account. Four steps: org → owner → Twilio → enable.

```sh
cd scripts

# 1. Create the org (+ settings row + "Unavailable" marker). Leave SMS off for now.
uv run create_organization.py \
  --org-name "Glamour Nails" \
  --business-phone "330-555-7777" \
  --timezone "America/New_York"

# 2. Create the owner login.
uv run bootstrap_organization_owner.py \
  --org-name "Glamour Nails" \
  --username "glamour-owner" \
  --password "<a strong password>"

# 3. Load the salon's own Twilio creds. SID/phone are not secret (pass as args);
#    the token is read from a hidden interactive prompt — never argv/history.
HISTFILE=/dev/null uv run set_org_twilio.py \
  --org-name "Glamour Nails" \
  --account-sid "ACxxxxxxxx" \
  --phone-number "+13305557777"
# prompts: "Twilio auth token: " (paste; input is hidden)
```

4. **Enable reminders:** the owner flips the SMS toggle in Settings (now unlocked,
   because Twilio is configured). The toggle stays locked until Twilio is loaded.

## Notes

- **Token hygiene:** for a new salon, step 3's hidden prompt keeps the token out of
  argv and shell history. For the existing salon, the token is already in `.env`.
- **Toggle gating:** owners can only enable SMS when Twilio is configured; the
  server rejects an enable attempt otherwise (400). A profile-only save never
  changes the stored SMS flag.
- An in-app admin role/UI to manage Twilio (replacing the script for this) is
  planned; until then, `set_org_twilio.py` is the credential path.
