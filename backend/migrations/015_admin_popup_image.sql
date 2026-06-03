-- Migration 015 — Global DB Admin popup image + assigned reasons

ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS popup_image      BYTEA,
  ADD COLUMN IF NOT EXISTS popup_image_type TEXT DEFAULT 'image/jpeg',
  ADD COLUMN IF NOT EXISTS popup_image_updated_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS assigned_reasons TEXT; -- JSON array e.g. '["Spam call","Phishing"]'

-- Index for fast image lookup
CREATE INDEX IF NOT EXISTS idx_admin_users_has_image
  ON admin_users(id) WHERE popup_image IS NOT NULL;
