-- ============================================================
-- Allow custom admin roles
-- ============================================================
-- Migration 014 created admin_users.role with an inline CHECK constraint that
-- only permitted the six original role names. Now that roles are managed in the
-- `roles` table, that constraint rejects any custom role (e.g. 'techadmin').
-- Drop it; role validity is enforced in the API against the roles table.

ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS admin_users_role_check;

-- Safety net: make sure every role currently in use exists in the roles table,
-- so a foreign key (added below) won't fail. Any unknown role is backfilled.
INSERT INTO roles (key, label, permissions, is_system)
  SELECT DISTINCT a.role, a.role, '[]'::jsonb, FALSE
    FROM admin_users a
   WHERE a.role IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.key = a.role)
ON CONFLICT (key) DO NOTHING;

-- Optional referential integrity: role must exist in roles(key).
-- ON UPDATE CASCADE keeps things consistent if a key is ever renamed.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
     WHERE constraint_name = 'admin_users_role_fkey'
       AND table_name = 'admin_users'
  ) THEN
    ALTER TABLE admin_users
      ADD CONSTRAINT admin_users_role_fkey
      FOREIGN KEY (role) REFERENCES roles(key)
      ON UPDATE CASCADE ON DELETE RESTRICT;
  END IF;
END $$;
