-- Add REGULYN_SUPER_ADMIN role and user for Platform Console
-- Run this manually in your PostgreSQL database (regulyn schema: identity)

-- 1. Create REGULYN_SUPER_ADMIN role (platform-level, not tenant-specific)
INSERT INTO roles (role_id, tenant_id, role_name, description) 
VALUES (
    '99999999-9999-9999-9999-999999999999',
    '11111111-1111-1111-1111-111111111111',  -- Using local test tenant
    'REGULYN_SUPER_ADMIN',
    'Platform super administrator with cross-tenant access'
) ON CONFLICT DO NOTHING;

-- 2. Create super admin user
-- Password: superadmin123
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhCm
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, enabled)
VALUES (
    '88888888-8888-8888-8888-888888888888',
    '11111111-1111-1111-1111-111111111111',
    'superadmin@regulyn.io',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhCm',
    'Super',
    'Admin',
    true
) ON CONFLICT DO NOTHING;

-- 3. Assign REGULYN_SUPER_ADMIN role to the user
INSERT INTO user_roles (user_id, role_id, tenant_id)
VALUES (
    '88888888-8888-8888-8888-888888888888',
    '99999999-9999-9999-9999-999999999999',
    '11111111-1111-1111-1111-111111111111'
) ON CONFLICT DO NOTHING;

-- Verify
SELECT 
    u.email,
    u.first_name,
    u.last_name,
    r.role_name
FROM users u
JOIN user_roles ur ON u.user_id = ur.user_id
JOIN roles r ON ur.role_id = r.role_id
WHERE u.email = 'superadmin@regulyn.io';
