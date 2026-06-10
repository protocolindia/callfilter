-- ============================================================
-- Per-user contacts-sync OVERRIDE (tri-state)
-- ============================================================
-- 'default' = follow the global contacts_sync_enabled switch
-- 'on'      = always enabled for this user (even if global is OFF)
-- 'off'     = always disabled for this user (even if global is ON)

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS contacts_sync_override TEXT NOT NULL DEFAULT 'default';

-- Back-fill from the old boolean flag: a previously-disabled user becomes 'off'.
UPDATE users SET contacts_sync_override = 'off'
  WHERE contacts_sync_allowed = FALSE
    AND contacts_sync_override = 'default';
