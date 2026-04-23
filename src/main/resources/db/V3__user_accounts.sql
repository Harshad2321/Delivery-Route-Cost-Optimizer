-- Delivery Route Cost Optimizer schema upgrade V3
-- Adds email-based accounts with explicit account_type and seeded admin logins.

ALTER TABLE app_users
  ADD COLUMN IF NOT EXISTS full_name VARCHAR(150) NULL,
  ADD COLUMN IF NOT EXISTS email VARCHAR(190) NULL,
  ADD COLUMN IF NOT EXISTS account_type VARCHAR(30) NULL;

-- Ensure existing data is mapped into new fields.
UPDATE app_users
SET full_name = COALESCE(NULLIF(full_name, ''), username)
WHERE full_name IS NULL OR full_name = '';

UPDATE app_users
SET email = COALESCE(NULLIF(email, ''), CONCAT(LOWER(username), '@example.local'))
WHERE (email IS NULL OR email = '') AND username IS NOT NULL AND username <> '';

UPDATE app_users
SET account_type = CASE
  WHEN UPPER(role) = 'DELIVERY' THEN 'DELIVERY_PARTNER'
  WHEN UPPER(role) = 'DELIVERY_PARTNER' THEN 'DELIVERY_PARTNER'
  WHEN UPPER(role) = 'ADMIN' THEN 'ADMIN'
  ELSE 'CUSTOMER'
END
WHERE account_type IS NULL OR account_type = '';

-- Enforce uniqueness for email login.
ALTER TABLE app_users
  ADD UNIQUE KEY IF NOT EXISTS uk_app_users_email (email);

-- Seed four fixed admin accounts if missing.
INSERT IGNORE INTO app_users (username, full_name, email, password, role, account_type, is_active)
VALUES
  ('admin1@drc.local', 'Admin One', 'admin1@drc.local', 'Admin@123', 'ADMIN', 'ADMIN', 1),
  ('admin2@drc.local', 'Admin Two', 'admin2@drc.local', 'Admin@234', 'ADMIN', 'ADMIN', 1),
  ('admin3@drc.local', 'Admin Three', 'admin3@drc.local', 'Admin@345', 'ADMIN', 'ADMIN', 1),
  ('admin4@drc.local', 'Admin Four', 'admin4@drc.local', 'Admin@456', 'ADMIN', 'ADMIN', 1);
