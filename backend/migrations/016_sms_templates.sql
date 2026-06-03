-- Per-user auto-reply SMS templates, stored as a JSON array of strings.
-- Synced from the Android app so templates survive reinstall / new versions.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS sms_templates TEXT;
