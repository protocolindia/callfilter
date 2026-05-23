-- ============================================================
-- Migration 011 — block reason categorization
-- Adds a reason column to blocked_calls so manual blocks via the
-- post-call popup can be categorized (Spam / Phishing / etc.)
-- ============================================================

ALTER TABLE blocked_calls
  ADD COLUMN IF NOT EXISTS reason TEXT;

-- Index for filtering admin views by reason
CREATE INDEX IF NOT EXISTS idx_blocked_calls_reason
  ON blocked_calls(reason)
  WHERE reason IS NOT NULL;
