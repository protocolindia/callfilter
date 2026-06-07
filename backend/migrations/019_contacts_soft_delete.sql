-- ============================================================
-- Contacts soft-delete + cross-device restore support
-- ============================================================

-- Soft delete: contacts removed on the phone are marked deleted, never purged,
-- so they can be restored / audited. NULL = active.
ALTER TABLE user_contacts
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Track last update so a re-added contact can be "undeleted".
ALTER TABLE user_contacts
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_user_contacts_active
  ON user_contacts (user_id) WHERE deleted_at IS NULL;
