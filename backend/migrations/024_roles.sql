-- ============================================================
-- Custom role management for web-panel admin logins
-- ============================================================
-- Roles become DB-driven so they can be created/edited with custom permissions
-- at both NAV (left-menu link) level and ACTION level. admin_users.role stores
-- the role key (already a TEXT column).

CREATE TABLE IF NOT EXISTS roles (
    id           SERIAL PRIMARY KEY,
    key          TEXT UNIQUE NOT NULL,        -- stored in admin_users.role
    label        TEXT NOT NULL,               -- human-friendly name
    permissions  JSONB NOT NULL DEFAULT '[]', -- array of permission keys; ['*'] = all
    is_system    BOOLEAN NOT NULL DEFAULT FALSE, -- system roles can't be deleted
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed the existing roles so current logins keep working unchanged.
-- Permission keys follow "nav.<area>" (left-menu links) and
-- "<area>.<action>" (action-level). super_admin keeps the wildcard.
INSERT INTO roles (key, label, permissions, is_system) VALUES
  ('super_admin', 'Super Admin', '["*"]', TRUE),
  ('admin', 'Admin',
   '["nav.dashboard","nav.users","nav.global_blocklist","nav.sms_protection","nav.billing","nav.payments","nav.block_reasons","nav.settings","nav.audit","nav.fraud_reports","users.view","users.edit","users.delete","users.reset_pin","users.contacts_view","users.rules_view","users.blocked_view","global_blocklist.view","global_blocklist.create","global_blocklist.edit","global_blocklist.delete","global_blocklist.import","sms_protection.manage","settings.edit","fraud_reports.view","billing.view","payments.view","block_reasons.edit"]',
   TRUE),
  ('support', 'Support',
   '["nav.dashboard","nav.users","nav.global_blocklist","users.view","users.reset_pin","users.contacts_view","users.rules_view","users.blocked_view","global_blocklist.view"]',
   TRUE),
  ('billing', 'Billing',
   '["nav.dashboard","nav.billing","nav.payments","nav.settings","billing.view","payments.view"]',
   TRUE),
  ('global_db_admin', 'Global DB Admin',
   '["nav.dashboard","nav.global_blocklist","nav.admin_users","global_blocklist.view","global_blocklist.create","global_blocklist.edit","global_blocklist.delete","global_blocklist.import","admin_users.children"]',
   TRUE),
  ('global_db_user', 'Global DB User',
   '["nav.dashboard","nav.global_blocklist","global_blocklist.view","global_blocklist.create","global_blocklist.edit","global_blocklist.delete","global_blocklist.own"]',
   TRUE)
ON CONFLICT (key) DO NOTHING;
