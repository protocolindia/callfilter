-- ============================================================
-- Migration 009 — v25.8: differential rules sync, activation flag,
-- one-time-only plans
--
-- v25.10 update: removed gen_random_uuid() / pgcrypto dependency.
-- Now uses 'legacy-{id}' placeholder for backfilled client_ids,
-- which is unique per row and doesn't need any extension.
-- ============================================================

-- Backfill any nulls so the unique index can be created
UPDATE user_rules
   SET client_id = 'legacy-' || id::text
 WHERE client_id IS NULL OR client_id = '';

-- Make client_id NOT NULL (idempotent — only if currently nullable)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_name = 'user_rules'
       AND column_name = 'client_id'
       AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE user_rules ALTER COLUMN client_id SET NOT NULL;
  END IF;
END $$;

-- Unique index for ON CONFLICT in /api/rules/add
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname  = 'idx_user_rules_user_client'
  ) THEN
    CREATE UNIQUE INDEX idx_user_rules_user_client
      ON user_rules(user_id, client_id);
  END IF;
END $$;

-- Plan flag: subscribe-once-per-user
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;
