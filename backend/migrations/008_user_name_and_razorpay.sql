-- ============================================================
-- Migration 008 — user names + Razorpay billing
-- ============================================================

-- Capture the registered user's name (asked on signup)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS name TEXT;

-- Distinguish how a subscription was paid for: 'play' or 'razorpay'
ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'play';

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS razorpay_order_id    TEXT;
ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS razorpay_payment_id  TEXT;
ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS razorpay_signature   TEXT;

-- Track every Razorpay order created (success or fail) for the admin tx log
CREATE TABLE IF NOT EXISTS razorpay_orders (
  id                BIGSERIAL PRIMARY KEY,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_id           INTEGER REFERENCES plans(id),
  order_id          TEXT NOT NULL UNIQUE,        -- order_xxx from Razorpay
  amount_paise      BIGINT NOT NULL,             -- amount in paise (no rupees!)
  currency          TEXT NOT NULL DEFAULT 'INR',
  status            TEXT NOT NULL DEFAULT 'created',
                    -- created | attempted | paid | failed | cancelled
  razorpay_payment_id TEXT,
  razorpay_signature  TEXT,
  notes             JSONB,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  paid_at           TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_user ON razorpay_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_status ON razorpay_orders(status);
CREATE INDEX IF NOT EXISTS idx_razorpay_orders_created ON razorpay_orders(created_at DESC);
