-- ============================================================
-- Migration 005 — billing module
--
-- Tables:
--   plans          — subscription tiers (single tier for now, but extensible)
--   coupons        — discount codes with validity windows
--   subscriptions  — per-user subscription record (active/expired/trialing)
--   payments       — payment events (filled in by v19 with Razorpay)
-- ============================================================

CREATE TABLE IF NOT EXISTS plans (
  id             SERIAL PRIMARY KEY,
  name           TEXT NOT NULL,
  duration_days  INTEGER NOT NULL,
  -- Both prices stored in paise (INR * 100). actual_price is the regular
  -- price; offer_price is the discounted price that's actually charged.
  actual_price   INTEGER NOT NULL,
  offer_price    INTEGER NOT NULL,
  currency       TEXT NOT NULL DEFAULT 'INR',
  is_active      BOOLEAN DEFAULT TRUE,
  created_at     TIMESTAMPTZ DEFAULT NOW(),
  updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS coupons (
  id              SERIAL PRIMARY KEY,
  code            TEXT UNIQUE NOT NULL,
  -- "percent" (e.g. 10 = 10%) or "flat" (paise)
  discount_type   TEXT NOT NULL CHECK (discount_type IN ('percent', 'flat')),
  discount_value  INTEGER NOT NULL,
  valid_from      TIMESTAMPTZ DEFAULT NOW(),
  valid_until     TIMESTAMPTZ NOT NULL,
  -- Optional usage cap. null = unlimited
  max_uses        INTEGER,
  uses_count      INTEGER DEFAULT 0,
  is_active       BOOLEAN DEFAULT TRUE,
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_coupons_code ON coupons(code);

CREATE TABLE IF NOT EXISTS subscriptions (
  id              BIGSERIAL PRIMARY KEY,
  user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_id         INTEGER REFERENCES plans(id) ON DELETE SET NULL,
  -- "trial" | "active" | "expired" | "cancelled"
  status          TEXT NOT NULL DEFAULT 'trial',
  starts_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at      TIMESTAMPTZ NOT NULL,
  is_trial        BOOLEAN DEFAULT FALSE,
  amount_paid     INTEGER,
  coupon_id       INTEGER REFERENCES coupons(id) ON DELETE SET NULL,
  payment_id      TEXT,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subs_user      ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subs_expires   ON subscriptions(expires_at);
CREATE INDEX IF NOT EXISTS idx_subs_user_active ON subscriptions(user_id, expires_at DESC);

CREATE TABLE IF NOT EXISTS payments (
  id                  BIGSERIAL PRIMARY KEY,
  user_id             INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_id             INTEGER REFERENCES plans(id) ON DELETE SET NULL,
  amount              INTEGER NOT NULL,
  currency            TEXT DEFAULT 'INR',
  -- "created" | "paid" | "failed" | "refunded"
  status              TEXT NOT NULL DEFAULT 'created',
  -- Razorpay specific (filled in v19)
  razorpay_order_id   TEXT,
  razorpay_payment_id TEXT,
  razorpay_signature  TEXT,
  coupon_id           INTEGER REFERENCES coupons(id) ON DELETE SET NULL,
  raw_payload         TEXT,
  created_at          TIMESTAMPTZ DEFAULT NOW(),
  updated_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_user ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(razorpay_order_id);

-- Default trial duration is stored in the existing settings table (key=trial_days)
INSERT INTO settings(key, value) VALUES ('trial_days', '7')
  ON CONFLICT (key) DO NOTHING;

-- Razorpay credentials (left blank until admin fills them in)
INSERT INTO settings(key, value) VALUES ('razorpay_key_id', '')
  ON CONFLICT (key) DO NOTHING;
INSERT INTO settings(key, value) VALUES ('razorpay_key_secret', '')
  ON CONFLICT (key) DO NOTHING;
INSERT INTO settings(key, value) VALUES ('razorpay_webhook_secret', '')
  ON CONFLICT (key) DO NOTHING;
INSERT INTO settings(key, value) VALUES ('razorpay_mode', 'test')
  ON CONFLICT (key) DO NOTHING;
