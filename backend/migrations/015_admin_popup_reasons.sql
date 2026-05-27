-- Migration 015 — global_db_admin popup image + assigned reasons

ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS assigned_reasons TEXT,     -- JSON array e.g. ["Spam call","Phishing"]
  ADD COLUMN IF NOT EXISTS popup_image_data TEXT,     -- base64-encoded image
  ADD COLUMN IF NOT EXISTS popup_image_mime TEXT      -- "image/jpeg" or "image/png"
    DEFAULT 'image/jpeg';
