-- ============================================================
-- Migration 009 — v25.8: differential rules sync, activation flag,
-- one-time-only plans
-- ============================================================

-- Required for ON CONFLICT in /api/rules/add and /api/rules/sync
-- The user_rules table may already have client_id rows with NULLs from
-- earlier migrations. Backfill any nulls with a generated UUID so the
-- unique index can be created.
UPDATE user_rules SET client_id = gen_random_uuid()::text
  WHERE client_id IS NULL OR client_id = '';

-- Make client_id NOT NULL
ALTER TABLE user_rules ALTER COLUMN client_id SET NOT NULL;

-- Unique per user. (user_id, client_id) is the natural sync key.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname = 'public'
                              AND indexname = 'idx_user_rules_user_client'
  ) THEN
    CREATE UNIQUE INDEX idx_user_rules_user_client
      ON user_rules(user_id, client_id);
  END IF;
END $$;

-- Plan flag: subscribe-once-per-user (free trial, one-shot upgrade, etc.)
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;

-- Note: users.status already exists (from migration 001). Admin uses
-- 'active' | 'disabled' values via the new admin endpoint added in v25.8.
-- No schema change needed.

-- Make sure gen_random_uuid is available (requires pgcrypto in most setups)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
