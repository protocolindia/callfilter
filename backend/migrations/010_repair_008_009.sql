-- ============================================================
-- Migration 010 — defensive replay of 008 + 009 schema changes
--
-- 010 exists because earlier versions of the migration runner
-- silently swallowed errors and may have marked 008 or 009 as
-- "applied" when they hadn't fully run. This migration brings
-- the schema up to the expected state idempotently.
-- ============================================================

-- From 008
ALTER TABLE users ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'play';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_order_id    TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_payment_id  TEXT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS razorpay_signature   TEXT;

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

-- From 009 — convert client_id INTEGER → TEXT (idempotent)
ALTER TABLE user_rules
  ALTER COLUMN client_id TYPE TEXT
  USING client_id::text;

-- Plan flag
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;
