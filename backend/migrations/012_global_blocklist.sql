-- ============================================================
-- Migration 012 — Global Blocklist
-- Numbers added by admin, optionally blocked by reason category.
-- ============================================================

CREATE TABLE IF NOT EXISTS global_blocklist (
  id          BIGSERIAL PRIMARY KEY,
  number      TEXT NOT NULL,
  reason      TEXT NOT NULL,
  notes       TEXT,
  added_by    TEXT NOT NULL DEFAULT 'admin',
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique active number (same number can't appear twice while active)
CREATE UNIQUE INDEX IF NOT EXISTS idx_global_blocklist_number_active
  ON global_blocklist(number)
  WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_global_blocklist_reason  ON global_blocklist(reason);
CREATE INDEX IF NOT EXISTS idx_global_blocklist_active  ON global_blocklist(active);
CREATE INDEX IF NOT EXISTS idx_global_blocklist_created ON global_blocklist(created_at DESC);
