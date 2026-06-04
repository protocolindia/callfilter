-- Per-user OTP delivery mode override.
--   'global'     = follow the global sms_provider setting (default)
--   'demo'       = always demo (OTP returned in response, shown on screen)
--   'production' = always send a real SMS via the configured gateway
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS otp_mode TEXT NOT NULL DEFAULT 'global';
