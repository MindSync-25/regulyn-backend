-- V7: Seed test data for ROPA Inventory service
-- Tenant: 8d047e8d-b550-4dff-8308-01ea1b4676ae (from identity service)

SET search_path TO ropa;

-- ─────────────────────────────────────────────────────────────────────────────
-- Systems
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ropa_systems (system_id, tenant_id, system_name, system_type, owner_team, location, criticality, enabled)
VALUES
    ('00000001-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Salesforce CRM', 'SAAS', 'Sales Ops', 'US', 'HIGH', true),

    ('00000001-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'HR Core Database', 'DATABASE', 'HR', 'INDIA', 'HIGH', true),

    ('00000001-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Mailchimp Marketing', 'SAAS', 'Marketing', 'US', 'MED', true)
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Data Categories
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ropa_data_categories (data_category_id, tenant_id, category_key, label, sensitive)
VALUES
    ('00000002-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'PII', 'Personal Identifiable Information', true),

    ('00000002-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'EMPLOYEE_DATA', 'Employee Records', true),

    ('00000002-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'FINANCIAL', 'Financial & Payment Data', true),

    ('00000002-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'DEVICE', 'Device Identifiers & Telemetry', false)
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Activity Versions
--   activity_id groups multiple versions of the same logical activity
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ropa_activity_versions
    (version_id, tenant_id, activity_id, version_number, status,
     activity_name, purpose, lawful_basis, data_principal_type,
     description, retention_days, risk_level, published_at)
VALUES
    -- Customer Onboarding (PUBLISHED)
    ('00000004-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000003-0000-0000-0000-000000000001',
     1, 'PUBLISHED',
     'Customer Onboarding',
     'Collect and verify customer identity for account creation',
     'CONTRACT', 'CUSTOMER',
     'Covers name, email, address, and ID verification data collected at signup.',
     1095, 'MED',
     NOW() - INTERVAL '30 days'),

    -- Employee Payroll Processing (PUBLISHED)
    ('00000004-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000003-0000-0000-0000-000000000002',
     1, 'PUBLISHED',
     'Employee Payroll Processing',
     'Process monthly salaries and statutory deductions under labour law',
     'LEGAL_OBLIGATION', 'EMPLOYEE',
     'Covers payroll computation, PF/ESI contributions, and income-tax deductions. Mandatory under Payment of Wages Act.',
     2555, 'HIGH',
     NOW() - INTERVAL '60 days'),

    -- Behavioural Analytics (DRAFT)
    ('00000004-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000003-0000-0000-0000-000000000003',
     1, 'DRAFT',
     'Behavioural Analytics',
     'Track user behaviour for product improvement and personalisation',
     'LEGITIMATE_INTERESTS', 'CUSTOMER',
     'Uses clickstream, session recordings, and cohort data to improve UX. Opt-out available.',
     365, 'MED',
     NULL),

    -- Legacy Marketing Emails (RETIRED)
    ('00000004-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000003-0000-0000-0000-000000000004',
     1, 'RETIRED',
     'Legacy Email Marketing',
     'Bulk promotional email campaigns to opted-in customers',
     'CONSENT', 'CUSTOMER',
     'Retired in favour of targeted consent-based campaigns. Data migrated to new activity.',
     180, 'LOW',
     NOW() - INTERVAL '180 days')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Activity ↔ Systems links
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ropa_activity_systems (id, tenant_id, version_id, system_id)
VALUES
    -- Customer Onboarding → Salesforce CRM
    ('00000009-0000-0000-0001-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000001',
     '00000001-0000-0000-0000-000000000001'),

    -- Payroll → HR Core Database
    ('00000009-0000-0000-0001-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000002',
     '00000001-0000-0000-0000-000000000002'),

    -- Behavioural Analytics → Salesforce CRM
    ('00000009-0000-0000-0001-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000003',
     '00000001-0000-0000-0000-000000000001'),

    -- Behavioural Analytics → Mailchimp Marketing
    ('00000009-0000-0000-0001-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000003',
     '00000001-0000-0000-0000-000000000003'),

    -- Legacy Email → Mailchimp Marketing
    ('00000009-0000-0000-0001-000000000005',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000004',
     '00000001-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Activity ↔ Data Category links
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ropa_activity_data_categories (id, tenant_id, version_id, data_category_id)
VALUES
    -- Customer Onboarding → PII
    ('0000000a-0000-0000-0001-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000001',
     '00000002-0000-0000-0000-000000000001'),

    -- Customer Onboarding → Financial
    ('0000000a-0000-0000-0001-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000001',
     '00000002-0000-0000-0000-000000000003'),

    -- Payroll → Employee Data
    ('0000000a-0000-0000-0001-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000002',
     '00000002-0000-0000-0000-000000000002'),

    -- Payroll → Financial
    ('0000000a-0000-0000-0001-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000002',
     '00000002-0000-0000-0000-000000000003'),

    -- Behavioural Analytics → PII
    ('0000000a-0000-0000-0001-000000000005',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000003',
     '00000002-0000-0000-0000-000000000001'),

    -- Behavioural Analytics → Device
    ('0000000a-0000-0000-0001-000000000006',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000003',
     '00000002-0000-0000-0000-000000000004'),

    -- Legacy Email → PII
    ('0000000a-0000-0000-0001-000000000007',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000004-0000-0000-0000-000000000004',
     '00000002-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;
