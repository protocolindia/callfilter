-- ============================================================
-- Migration 010 — repair v25.8 schema (no pgcrypto required)
--
-- Migration 010 likely failed on Railway because CREATE EXTENSION
-- pgcrypto is not allowed on most managed Postgres instances.
-- This migration achieves the same end state WITHOUT needing
-- pgcrypto. Client IDs are filled with a deterministic placeholder
-- ('legacy-' + row id) rather than a random UUID — fine for old
-- pre-v25.8 rows, since the app will re-sync them on first launch.
-- ============================================================

-- 1. users.name
ALTER TABLE users ADD COLUMN IF NOT EXISTS name TEXT;

-- 2. subscriptions provider columns
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'play';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_order_id    TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_payment_id  TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_signature   TEXT;

-- 3. razorpay_orders table
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
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_user    ON razorpay_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_status  ON razorpay_orders(status);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_created ON razorpay_orders(created_at DESC);

-- 4. plans.is_one_time_per_user — THE COLUMN THAT'S MISSING
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. Backfill any NULL client_ids using a deterministic placeholder
--    (NOT a UUID — that would need pgcrypto). 'legacy-{row_id}' is unique
--    per row, which satisfies the upcoming unique index.
UPDATE user_rules
   SET client_id = 'legacy-' || id::text
 WHERE client_id IS NULL OR client_id = '';

-- 6. Make client_id NOT NULL (idempotent)
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

-- 7. Unique index for ON CONFLICT in /api/rules/add
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
