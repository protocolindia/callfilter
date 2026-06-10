-- ============================================================
-- Grant per-tab Settings permissions to the built-in 'admin' role
-- ============================================================
-- The settings.<tab> permission keys were added after roles were seeded.
-- Ensure the 'admin' role still sees every Settings tab by adding them.

UPDATE roles
   SET permissions = (
         SELECT jsonb_agg(DISTINCT p)
           FROM jsonb_array_elements(
                  permissions ||
                  '["settings.sms","settings.otp","settings.subscription",
                    "settings.razorpay","settings.contacts","settings.fraud",
                    "settings.password"]'::jsonb
                ) AS p
       ),
       updated_at = NOW()
 WHERE key = 'admin';
