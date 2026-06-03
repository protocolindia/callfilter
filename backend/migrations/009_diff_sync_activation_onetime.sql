-- ============================================================
-- Migration 009 — v25.8: differential rules sync, one-time-only plans
--
-- v25.10 update: the ROOT CAUSE of the rules sync bug was that
-- user_rules.client_id was declared INTEGER in migration 002, but
-- the Android app sends UUID strings like "a1b2c3d4-...". Every
-- /api/rules/add request was hitting Postgres with a type error
-- on the integer cast. This migration converts client_id to TEXT
-- so UUIDs work.
--
-- migration 002 already declared UNIQUE (user_id, client_id) and
-- created the underlying index, so we don't need to add either.
-- ============================================================

-- Convert client_id INTEGER → TEXT. USING clause makes it idempotent
-- against an already-text column (cast TEXT to TEXT is a no-op).
ALTER TABLE user_rules
  ALTER COLUMN client_id TYPE TEXT
  USING client_id::text;

-- Plan flag: subscribe-once-per-user
ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS is_one_time_per_user BOOLEAN NOT NULL DEFAULT FALSE;
