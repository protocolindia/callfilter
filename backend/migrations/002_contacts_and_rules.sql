-- ============================================================
-- Migration 002 — contact upload + rule sync
-- ============================================================

-- Per-user uploaded contacts (only stored when user opted in)
CREATE TABLE IF NOT EXISTS user_contacts (
  id           BIGSERIAL PRIMARY KEY,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  display_name TEXT,
  phone_number TEXT NOT NULL,
  -- normalized (digits only, no +/-/spaces) — used for dedup
  normalized   TEXT NOT NULL,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, normalized)
);

CREATE INDEX IF NOT EXISTS idx_user_contacts_user ON user_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_user_contacts_normalized ON user_contacts(normalized);

-- Per-user blocking/allow rules (mirrors what's stored on the device)
CREATE TABLE IF NOT EXISTS user_rules (
  id          BIGSERIAL PRIMARY KEY,
  user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- "prefix" | "suffix" | "between"
  rule_type   TEXT NOT NULL,
  pattern     TEXT NOT NULL,
  -- "accept" | "reject"
  action      TEXT NOT NULL,
  -- device-side rule id, so the app can re-sync without duplicates
  client_id   INTEGER,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, client_id)
);

CREATE INDEX IF NOT EXISTS idx_user_rules_user ON user_rules(user_id);

-- Track whether a user has opted in to contacts upload, and last sync timestamp
ALTER TABLE users ADD COLUMN IF NOT EXISTS contacts_opted_in    BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS contacts_opted_in_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_contacts_sync   TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_rules_sync      TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS contacts_count       INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rules_count          INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_hash             TEXT;
