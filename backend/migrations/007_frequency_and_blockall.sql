-- ============================================================
-- Migration 007 — schedule frequency-bypass + Block All Now
-- ============================================================
-- Frequency-bypass: each schedule may allow an "urgent caller break-through"
-- — if the same number is rejected freq_count times within freq_window_min
-- minutes, the NEXT call from that number rings through. Sliding window,
-- per-number. Tracked on the client; server just stores config.
--
-- Block All Now (panic mode) is a SEPARATE session state on the client,
-- not in the schedules table. We store its current state per-user so it
-- survives reinstalls.
-- ============================================================

ALTER TABLE schedules
  ADD COLUMN IF NOT EXISTS freq_bypass_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS freq_count          INTEGER NOT NULL DEFAULT 5,
  ADD COLUMN IF NOT EXISTS freq_window_min     INTEGER NOT NULL DEFAULT 10;

-- Block All Now: one row per user describing current panic mode state
CREATE TABLE IF NOT EXISTS block_all_state (
  user_id           INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  -- 'everything' | 'except_contacts' | 'except_custom' | null (= off)
  mode              TEXT,
  -- epoch ms; null = indefinite ("until I turn it off"). Past = expired/off.
  expires_at_ms     BIGINT,
  -- For mode='except_custom': allowlist of numbers + matching display names
  allow_numbers     JSONB NOT NULL DEFAULT '[]'::jsonb,
  allow_names       JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
