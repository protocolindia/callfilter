-- ============================================================
-- Migration 010 — v25.9: defensively reapply 008+009 changes
--
-- Background: migration 009 was incorrectly marked "applied" on some
-- deploys because the migration runner caught "does not exist" errors
-- (from gen_random_uuid before pgcrypto was installed) and skipped the
-- rest of 009's statements. This migration brings the schema up to the
-- expected state idempotently, regardless of what 008/009 actually did.
-- ============================================================

-- 1. pgcrypto FIRST (needed for gen_random_uuid)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. users.name (from 008)
ALTER TABLE users ADD COLUMN IF NOT EXISTS name TEXT;

-- 3. subscriptions provider columns (from 008)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'play';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_order_id    TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_payment_id  TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_signature   TEXT;

-- 4. razorpay_orders table (from 008)
CREATE TABLE IF NOT EXISTS razorpay_orders (
  id                BIGSERIAL PRIMARY KEY,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_id           INTEGER REFERENCES plans(id),
  order_id          TEXT NOT NULL UNIQUE,
  amount_paise      BIGINT NOT NULL,
  currency          TEXT NOT NULL DEFAULT 'INR',
  status            TEXT NOT NULL DEFAULT 'created',
  razorpay_payment_id TEXT,
  razorpay_signature  TEXT,
  notes             JSONB,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  paid_at           TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_user ON razorpay_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_status ON razorpay_orders(status);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_created ON razorpay_orders(created_at DESC);

-- 5. plans.is_one_time_per_user (from 009) — THE COLUMN THAT WAS MISSING
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;

-- 6. Backfill any NULL client_ids before adding NOT NULL constraint
UPDATE user_rules SET client_id = gen_random_uuid()::text
  WHERE client_id IS NULL OR client_id = '';

-- 7. Make client_id NOT NULL (idempotent — guarded check)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_name = 'user_rules' AND column_name = 'client_id'
       AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE user_rules ALTER COLUMN client_id SET NOT NULL;
  END IF;
END $$;

-- 8. Unique index for ON CONFLICT clause in /api/rules/add
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
