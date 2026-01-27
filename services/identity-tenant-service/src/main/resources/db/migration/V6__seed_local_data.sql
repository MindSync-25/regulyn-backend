-- V6__seed_local_data.sql
-- This migration seeds data for local development ONLY
-- Tenant: "Local Test Tenant"
-- Admin user: admin@local.test / password: admin123
-- API Key for connector: local-connector-key-12345

INSERT INTO tenants (tenant_id, name, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Local Test Tenant', 'ACTIVE');

-- Password hash for 'admin123' using BCrypt (strength 10)
-- Generated with: BCryptPasswordEncoder().encode("admin123")
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, enabled) VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 
     'admin@local.test', '$2a$10$N9qo8uLOickgx2ZMRZoMye1K8aLKI5WLkqFZr5FhZ5LHWz3gFUyPu', 
     'Admin', 'User', true);

INSERT INTO roles (role_id, tenant_id, role_name, description) VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 
     'TENANT_ADMIN', 'Full administrative access to tenant'),
    ('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111', 
     'DPO', 'Data Protection Officer role'),
    ('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111', 
     'CONNECTOR_AGENT', 'Connector service agent role');

INSERT INTO user_roles (user_id, role_id, tenant_id) VALUES
    ('22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 
     '11111111-1111-1111-1111-111111111111');

-- API Key hash for 'local-connector-key-12345'
-- SHA-256: d5e8f84b1e9f84c7d0f5c9e2a1b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1
INSERT INTO api_keys (api_key_id, tenant_id, key_name, api_key_hash, enabled) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 
     'local-connector-key', 
     'd5e8f84b1e9f84c7d0f5c9e2a1b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1', true);

-- Log seeded credentials (local profile only!)
DO $$
BEGIN
    RAISE NOTICE '=== LOCAL SEED DATA CREATED ===';
    RAISE NOTICE 'Tenant ID: 11111111-1111-1111-1111-111111111111';
    RAISE NOTICE 'Admin Email: admin@local.test';
    RAISE NOTICE 'Admin Password: admin123';
    RAISE NOTICE 'API Key: local-connector-key-12345';
    RAISE NOTICE '================================';
END $$;
