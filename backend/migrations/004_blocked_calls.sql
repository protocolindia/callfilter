-- ============================================================
-- Migration 004 — blocked call log
-- Each row = one rejected incoming call on a user's device.
-- ============================================================
CREATE TABLE IF NOT EXISTS blocked_calls (
  id              BIGSERIAL PRIMARY KEY,
  user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- Stable client-side identifier so re-syncing the same blocked call
  -- from the device doesn't create duplicates.
  client_id       TEXT NOT NULL,
  number          TEXT,
  -- "prefix" | "suffix" | "between" | "contacts_only" | "contact"
  rule_type       TEXT,
  rule_pattern    TEXT,
  -- "reject" almost always; "accept" rare for symmetry/whitelist actions
  rule_action     TEXT,
  -- Time the call arrived on the device, in epoch millis (kept as bigint),
  -- and a Postgres timestamp for ordering / grouping.
  blocked_at_ms   BIGINT NOT NULL,
  blocked_at      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, client_id)
);

CREATE INDEX IF NOT EXISTS idx_blocked_calls_user_time
  ON blocked_calls(user_id, blocked_at DESC);

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS blocked_calls_count INTEGER DEFAULT 0;
