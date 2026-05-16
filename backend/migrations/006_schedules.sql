-- ============================================================
-- Migration 006 — call-blocking schedules
-- ============================================================
-- A "schedule" is a named time window during which extra blocking rules
-- apply. While a schedule is "active" (current time falls within its window
-- on an allowed day, AND its on/off flag is true), the call blocker:
--   1. Applies all the user's normal rules (existing behavior), AND
--   2. Also rejects any caller NOT in this schedule's allowlist.
--
-- Quick-activate: a schedule can be temporarily forced on for N minutes
-- regardless of the regular time window. quick_until_ms is the epoch-ms
-- at which the quick activation expires.
-- ============================================================

CREATE TABLE IF NOT EXISTS schedules (
  id              BIGSERIAL PRIMARY KEY,
  user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id       TEXT NOT NULL,            -- client-generated UUID
  name            TEXT NOT NULL,
  start_minute    INTEGER NOT NULL,         -- 0..1439 (00:00 .. 23:59)
  end_minute      INTEGER NOT NULL,         -- 0..1439, may wrap (e.g. 22:00 -> 07:00)
  days_mask       INTEGER NOT NULL DEFAULT 127,  -- bit 0=Sun, 6=Sat. 127 = every day.
  is_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  -- Allowlist: array of contact phone numbers (E.164 strings). Picked from device contacts.
  allow_numbers   JSONB NOT NULL DEFAULT '[]'::jsonb,
  -- Allowlist with display names so the picker can show them later
  allow_names     JSONB NOT NULL DEFAULT '[]'::jsonb,
  -- Quick-activate: forces the schedule on until this timestamp (epoch ms)
  quick_until_ms  BIGINT,
  -- Resolves overlap: most-recently-toggled wins
  last_toggled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, client_id)
);

CREATE INDEX IF NOT EXISTS idx_schedules_user ON schedules(user_id);
