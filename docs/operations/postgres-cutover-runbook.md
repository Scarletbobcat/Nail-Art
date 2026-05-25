# PostgreSQL Cutover Runbook

Operational checklist for the MongoDB to PostgreSQL production cutover. Keep this checklist concrete; background lives in [`docs/reference/deployment.md`](../reference/deployment.md) and local command setup lives in [`docs/reference/local-development.md`](../reference/local-development.md).

## 1. Pre-cutover (T-1 day)

- [ ] Confirm the target branch is `postgres-migration-cutover` and the single migration PR contains all backend, frontend, cron, docs, and runbook commits.
- [ ] Confirm the cutover window is outside business hours and does not overlap the 3 PM ET SMS reminder schedule.
- [ ] Confirm local migration dependencies are installed:

  ```sh
  cd migration
  uv sync
  ```

- [ ] Export the source Mongo connection for the audit:

  ```sh
  export MONGO_URI="<PROD_MONGO_URI>"
  export MONGO_DB="Nail-Art"
  ```

- [ ] Run the read-only audit. `migration/audit_mongo.py` is retained specifically as the pre-cutover source-data verification tool.

  ```sh
  cd migration
  uv run python audit_mongo.py
  ```

- [ ] Resolve every audit blocker before proceeding.
- [ ] Identify the Mongo service id for the current unavailable/time-off service:

  ```sh
  export UNAVAILABILITY_MONGO_ID="<unavailability-mongo-id>"
  ```

- [ ] Record source row counts from the audit output for users, clients, employees, services, live appointments, archived appointments, and refresh tokens.

## 2. Pre-cutover (T-1 hour)

- [ ] Add the current laptop IP to the Render PostgreSQL allowlist.
- [ ] Export production target settings. Use the external PostgreSQL URL format for migration scripts, not the API's `jdbc:` URL.

  ```sh
  export POSTGRES_URL="<EXTERNAL_POSTGRES_URL>"
  export MONGO_URI="<PROD_MONGO_URI>"
  export MONGO_DB="Nail-Art"
  ```

- [ ] Run a dry-run migration. `--dry-run` executes inside one PostgreSQL transaction and explicitly rolls it back; a dropped connection also rolls back because the transaction never commits.

  ```sh
  cd migration
  uv run python migrate_mongo_to_postgres.py \
    --org-name "Nail Art & Spa LLC." \
    --org-phone "<business-phone>" \
    --org-timezone "America/New_York" \
    --owner-username "<owner-username>" \
    --owner-email "<owner-email>" \
    --owner-password "<owner-password>" \
    --unavailability-service-mongo-id "<unavailability-mongo-id>" \
    --dry-run
  ```

- [ ] Compare dry-run source row counts against the T-1 day audit counts.
- [ ] Compare dry-run PostgreSQL row counts against expected target counts:
  - [ ] `organizations = 1`
  - [ ] `organization_users = 1`
  - [ ] `organization_settings = 1`
  - [ ] `users = 1`
  - [ ] `employees` matches source employees
  - [ ] `services` matches source services
  - [ ] `clients` matches source clients
  - [ ] `appointments` matches live + archived appointments
  - [ ] `appointment_services` is plausible for the source appointments' service links
- [ ] Spot-check the unavailable service marker:

  ```sh
  psql "<EXTERNAL_POSTGRES_URL>" \
    -c "select name, is_unavailability_marker from services where is_unavailability_marker = true;"
  ```

- [ ] Block the real cutover if row counts or marker selection are wrong.

## 3. Cutover (T)

- [ ] Confirm the salon is closed and no one is using the app.
- [ ] Confirm the cutover window does not span the 3 PM ET reminder cron.
- [ ] Do not scale Render API to zero for this solo-operator cutover. With the salon closed, there are no expected human writes; keeping the rollback path simple is preferred.
- [ ] Keep `PROD_MONGO_URI` and `DEV_MONGO_URI` in Render during the cutover. They remain in place until T+1 day so rollback can redeploy the prior image without reconstructing env vars from memory.
- [ ] Run the real migration. The script is destructive in commit mode: it truncates application tables in the target PostgreSQL database and commits the replacement data. Only run this during the actual cutover window.

  ```sh
  cd migration
  uv run python migrate_mongo_to_postgres.py \
    --org-name "Nail Art & Spa LLC." \
    --org-phone "<business-phone>" \
    --org-timezone "America/New_York" \
    --owner-username "<owner-username>" \
    --owner-email "<owner-email>" \
    --owner-password "<owner-password>" \
    --unavailability-service-mongo-id "<unavailability-mongo-id>"
  ```

- [ ] Verify the command prints `Migration committed.`
- [ ] Deploy the new API image from the migration PR merge.
- [ ] If triggering Render manually, use the API service id:

  ```sh
  curl -X POST "https://api.render.com/v1/services/<RENDER_SERVICE_ID>/deploys" \
    -H "Authorization: Bearer <render-api-token>" \
    -H "Content-Type: application/json" \
    -d '{}'
  ```

- [ ] Confirm the API has these production env vars:
  - [ ] `POSTGRES_URL`
  - [ ] `POSTGRES_USER`
  - [ ] `POSTGRES_PASSWORD`
  - [ ] `PROD_FRONTEND_URL`
  - [ ] `JWT_SECRET_KEY`
  - [ ] `JWT_EXPIRATION`
  - [ ] `JWT_REFRESH_EXPIRATION`
  - [ ] `TWILIO_ACCOUNT_SID`
  - [ ] `TWILIO_AUTH_TOKEN`
  - [ ] `TWILIO_PHONE_NUMBER`
- [ ] Add staff accounts through the owner-gated endpoint if needed:

  ```sh
  curl -X POST "https://<api-host>/users" \
    -H "Authorization: Bearer <owner-token>" \
    -H "Content-Type: application/json" \
    -d '{"username":"<staff-username>","password":"<staff-password>","role":"staff"}'
  ```

## 4. Post-cutover (T+15 min)

- [ ] Manual E2E, desktop viewport:
  - [ ] Login with the migrated owner account.
  - [ ] Open the calendar.
  - [ ] Create an appointment.
  - [ ] Edit that appointment.
  - [ ] Delete that appointment.
  - [ ] Search appointments by partial phone number.
  - [ ] Confirm an unavailable/time-off appointment uses the special unavailable color.
  - [ ] Logout.
  - [ ] Login again.
- [ ] Manual E2E, mobile viewport:
  - [ ] Login with the migrated owner account.
  - [ ] Open the calendar.
  - [ ] Create an appointment.
  - [ ] Edit that appointment.
  - [ ] Delete that appointment.
  - [ ] Search appointments by partial phone number.
  - [ ] Confirm an unavailable/time-off appointment uses the special unavailable color.
  - [ ] Logout.
  - [ ] Login again.
- [ ] Confirm API logs show no tenant-context or database connection errors.
- [ ] Confirm no reminder cron ran during the migration/deploy window.
- [ ] Remove the laptop IP from the Render PostgreSQL allowlist.

## 5. Post-cutover (T+1 day)

- [ ] Confirm staff used the app successfully for one business day.
- [ ] Confirm appointment create/edit/delete/search and login/refresh/logout have no new production errors.
- [ ] Remove Mongo env vars from Render:
  - [ ] `PROD_MONGO_URI`
  - [ ] `DEV_MONGO_URI`
- [ ] Pause the Mongo Atlas cluster. Do not delete it yet.
- [ ] Keep the migration PR, migration output, and row-count notes available for rollback review.

## 6. Rollback path

- [ ] Use rollback only if production cannot be stabilized quickly on the PostgreSQL image.
- [ ] Option A: revert the cutover merge and redeploy the prior API image:

  ```sh
  git revert -m 1 <cutover-merge-sha>
  git push
  ```

- [ ] Option B: reset `main` to the prior known-good commit and redeploy the prior API image:

  ```sh
  git checkout main
  git reset --hard <prior-known-good-sha>
  git push --force-with-lease origin main
  ```

- [ ] Un-pause Mongo Atlas if it was already paused.
- [ ] Confirm the old API image still has Mongo env vars available in Render.
- [ ] No Mongo restore is needed. The migration script reads from Mongo and writes to PostgreSQL only; it does not mutate Mongo.
- [ ] Re-run smoke checks on the restored app:
  - [ ] Login.
  - [ ] Open calendar.
  - [ ] Search appointment.
  - [ ] Create and delete a test appointment.

## 7. Deletion (T+2 weeks)

- [ ] Confirm no rollback happened during the two-week observation window.
- [ ] Confirm production backups and migration notes are retained.
- [ ] Delete the Mongo Atlas cluster.
- [ ] Remove any remaining local or password-manager entries that only existed for the old Mongo runtime.
- [ ] Close the cutover tracking issue.
