-- ============================================================
-- Migration 014 — Multi-admin with roles
-- ============================================================

CREATE TABLE IF NOT EXISTS admin_users (
  id            BIGSERIAL PRIMARY KEY,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name  TEXT,
  role          TEXT NOT NULL DEFAULT 'admin'
                CHECK (role IN ('super_admin','admin','support','billing',
                                'global_db_admin','global_db_user')),
  parent_id     BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
  created_by    BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at    TIMESTAMPTZ   -- soft delete
);

CREATE INDEX IF NOT EXISTS idx_admin_users_role      ON admin_users(role);
CREATE INDEX IF NOT EXISTS idx_admin_users_parent    ON admin_users(parent_id);
CREATE INDEX IF NOT EXISTS idx_admin_users_active    ON admin_users(active, deleted_at);

-- Add admin tracking + soft delete to global_blocklist
ALTER TABLE global_blocklist
  ADD COLUMN IF NOT EXISTS added_by_admin_id BIGINT
    REFERENCES admin_users(id) ON DELETE SET NULL;

ALTER TABLE global_blocklist
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Back-fill existing blocklist entries so they show as super_admin's
-- (will be updated after seeding)
CREATE INDEX IF NOT EXISTS idx_global_blocklist_admin_id
  ON global_blocklist(added_by_admin_id);
CREATE INDEX IF NOT EXISTS idx_global_blocklist_active
  ON global_blocklist(deleted_at) WHERE deleted_at IS NULL;
