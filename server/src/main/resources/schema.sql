-- One-time cleanup after auth was reduced to username + password.
-- Runs on every boot (spring.sql.init.mode=always) but is fully idempotent.
-- Safe to delete once it has run against every environment.
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS email;
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS email_verified;
DROP TABLE IF EXISTS account_tokens;
