-- ============================================================
-- Migration 003 — store full contact info (multi-phone, emails,
-- addresses, orgs, websites, events, notes, photo, starred)
--
-- We replace the old user_contacts schema with a new structure:
--   user_contacts        — one row per Android contact (by client contact_id)
--   user_contact_phones  — multiple phones per contact
--   user_contact_emails  — multiple emails per contact
--   user_contact_addresses
--   user_contact_orgs
--   user_contact_websites
--   user_contact_events
-- ============================================================

-- Save then drop the old simple table (migration is destructive — old data will be re-synced
-- by phones on next app launch since we don't know the original contact_ids)
DROP TABLE IF EXISTS user_contacts CASCADE;

CREATE TABLE IF NOT EXISTS user_contacts (
  id           BIGSERIAL PRIMARY KEY,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_contact_id TEXT NOT NULL,
  display_name TEXT,
  photo_uri    TEXT,
  starred      BOOLEAN DEFAULT FALSE,
  notes        TEXT,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, client_contact_id)
);

CREATE INDEX IF NOT EXISTS idx_user_contacts_user ON user_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_user_contacts_name ON user_contacts(user_id, display_name);

CREATE TABLE IF NOT EXISTS user_contact_phones (
  id          BIGSERIAL PRIMARY KEY,
  contact_id  BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  number      TEXT NOT NULL,
  normalized  TEXT NOT NULL,
  type        TEXT
);
CREATE INDEX IF NOT EXISTS idx_phones_contact    ON user_contact_phones(contact_id);
CREATE INDEX IF NOT EXISTS idx_phones_normalized ON user_contact_phones(normalized);

CREATE TABLE IF NOT EXISTS user_contact_emails (
  id          BIGSERIAL PRIMARY KEY,
  contact_id  BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  address     TEXT NOT NULL,
  type        TEXT
);
CREATE INDEX IF NOT EXISTS idx_emails_contact ON user_contact_emails(contact_id);

CREATE TABLE IF NOT EXISTS user_contact_addresses (
  id                BIGSERIAL PRIMARY KEY,
  contact_id        BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  formatted_address TEXT,
  street            TEXT,
  city              TEXT,
  region            TEXT,
  postcode          TEXT,
  country           TEXT,
  type              TEXT
);
CREATE INDEX IF NOT EXISTS idx_addresses_contact ON user_contact_addresses(contact_id);

CREATE TABLE IF NOT EXISTS user_contact_orgs (
  id          BIGSERIAL PRIMARY KEY,
  contact_id  BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  company     TEXT,
  title       TEXT,
  department  TEXT
);
CREATE INDEX IF NOT EXISTS idx_orgs_contact ON user_contact_orgs(contact_id);

CREATE TABLE IF NOT EXISTS user_contact_websites (
  id          BIGSERIAL PRIMARY KEY,
  contact_id  BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  url         TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_websites_contact ON user_contact_websites(contact_id);

CREATE TABLE IF NOT EXISTS user_contact_events (
  id          BIGSERIAL PRIMARY KEY,
  contact_id  BIGINT NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
  date_text   TEXT NOT NULL,
  type        TEXT
);
CREATE INDEX IF NOT EXISTS idx_events_contact ON user_contact_events(contact_id);

-- Reset sync tracking so all users do a full re-sync on next launch
UPDATE users SET last_contacts_sync = NULL, contacts_count = 0 WHERE id > 0;
