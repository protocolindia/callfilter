-- Migration 013 — per-user global blocklist configuration
-- Stores which reason categories each user has enabled in the app

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS global_enabled_reasons TEXT;  -- JSON array string

-- Index for faster admin queries
CREATE INDEX IF NOT EXISTS idx_users_global_reasons
  ON users(id)
  WHERE global_enabled_reasons IS NOT NULL;
