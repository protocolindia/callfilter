-- ============================================================
-- Admin control of contacts sync (global + per-user)
-- ============================================================

-- Per-user override. TRUE = this user may use contacts sync (default).
-- An admin can set it FALSE to disable sync for one user.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS contacts_sync_allowed BOOLEAN NOT NULL DEFAULT TRUE;

-- Global master switch. 'true' = feature available to everyone (subject to the
-- per-user flag); 'false' = disabled for ALL users regardless of their flag.
INSERT INTO settings (key, value) VALUES ('contacts_sync_enabled', 'true')
ON CONFLICT (key) DO NOTHING;
