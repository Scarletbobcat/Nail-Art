-- Per-organization Twilio credentials on organization_settings.
-- sid and phone are plain identifiers; auth_token is pgcrypto ciphertext
-- (pgp_sym_encrypt -> bytea). The pgcrypto extension is already enabled in V1.
alter table organization_settings
    add column twilio_account_sid text,
    add column twilio_phone_number text,
    add column twilio_auth_token bytea;
