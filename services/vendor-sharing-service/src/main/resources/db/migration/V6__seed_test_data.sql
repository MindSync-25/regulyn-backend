-- V6: Seed test data for vendor-sharing-service
-- Tenant: 8d047e8d-b550-4dff-8308-01ea1b4676ae
-- Cross-references ROPA system/activity UUIDs from ROPA seed (V7)

-- ─────────────────────────────────────────────────────────────────────────────
-- Vendors
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO vendor.vendors
    (vendor_id, tenant_id, vendor_name, vendor_type, contact_email,
     country, hosting_region, risk_level, enabled)
VALUES
    ('00000005-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Salesforce Inc', 'PROCESSOR',
     'privacy@salesforce.com', 'US', 'US', 'MED', true),

    ('00000005-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Amazon Web Services', 'SUB_PROCESSOR',
     'aws-privacy@amazon.com', 'US', 'INDIA', 'LOW', true),

    ('00000005-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Mailchimp / Intuit', 'SERVICE_PROVIDER',
     'privacy@mailchimp.com', 'US', 'US', 'MED', true),

    ('00000005-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Razorpay', 'PROCESSOR',
     'dpo@razorpay.com', 'IN', 'INDIA', 'HIGH', true)
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Vendor Agreements
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO vendor.vendor_agreements
    (agreement_id, tenant_id, vendor_id, agreement_type, status,
     effective_from, effective_to, doc_ref, notes)
VALUES
    ('0000000b-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000001',
     'DPA', 'ACTIVE',
     '2024-01-01', '2026-12-31',
     'docs/dpa-salesforce-2024.pdf',
     'Data Processing Agreement covering CRM customer data'),

    ('0000000b-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000002',
     'DPA', 'ACTIVE',
     '2024-01-01', NULL,
     'docs/dpa-aws-2024.pdf',
     'AWS DPA covering all hosted data including backups'),

    ('0000000b-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000003',
     'MSA', 'ACTIVE',
     '2024-06-01', '2026-05-31',
     'docs/msa-mailchimp-2024.pdf',
     'Master Services Agreement with SCC addendum for cross-border transfers'),

    ('0000000b-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000004',
     'DPA', 'ACTIVE',
     '2024-03-01', '2026-02-28',
     'docs/dpa-razorpay-2024.pdf',
     'Payment processor DPA under PCI-DSS and RBI guidelines'),

    ('0000000b-0000-0000-0000-000000000005',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000004',
     'NDA', 'EXPIRED',
     '2023-01-01', '2024-01-01',
     'docs/nda-razorpay-2023.pdf',
     'Expired NDA — superseded by DPA')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Sharing Records
-- (activity_id/system_id reference ROPA service UUIDs — cross-service refs)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO vendor.sharing_records
    (sharing_id, tenant_id, vendor_id, activity_id, system_id,
     sharing_purpose, lawful_basis, data_categories, frequency,
     transfer_cross_border, transfer_to_regions, status, enabled,
     start_at)
VALUES
    -- Salesforce: CRM sync for customer onboarding
    ('0000000c-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000001',
     '00000003-0000-0000-0000-000000000001',   -- Customer Onboarding activity
     '00000001-0000-0000-0000-000000000001',   -- Salesforce CRM system
     'CRM data sync — customer profiles and activity history',
     'CONTRACT',
     ARRAY['PII', 'FINANCIAL'],
     'ONGOING', true, ARRAY['US'],
     'ACTIVE', true,
     '2024-01-15 00:00:00+00'),

    -- AWS: Cloud hosting for all application data
    ('0000000c-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000002',
     NULL,
     NULL,
     'Cloud infrastructure hosting for all application data (IaaS)',
     'CONTRACT',
     ARRAY['PII', 'EMPLOYEE_DATA', 'FINANCIAL'],
     'ONGOING', false, NULL,
     'ACTIVE', true,
     '2024-01-01 00:00:00+00'),

    -- Mailchimp: Email marketing list export
    ('0000000c-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000003',
     '00000003-0000-0000-0000-000000000004',   -- Legacy Email Marketing activity
     '00000001-0000-0000-0000-000000000003',   -- Mailchimp system
     'Opted-in email list for promotional campaigns',
     'CONSENT',
     ARRAY['PII'],
     'PER_REQUEST', true, ARRAY['US'],
     'INACTIVE', false,
     '2023-06-01 00:00:00+00'),

    -- Razorpay: Payment processing
    ('0000000c-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000004',
     '00000003-0000-0000-0000-000000000001',   -- Customer Onboarding activity
     NULL,
     'Payment card data for transaction processing',
     'CONTRACT',
     ARRAY['FINANCIAL', 'PII'],
     'PER_REQUEST', false, NULL,
     'ACTIVE', true,
     '2024-03-01 00:00:00+00')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Access Telemetry Events
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO vendor.vendor_access_events
    (tenant_id, vendor_id, system_name, source, access_type,
     subject_ref, data_categories, purpose_ref, accessed_at,
     correlation_id, actor_type, actor_id, ip, result,
     raw_payload_hash)
VALUES
    ('8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000001',
     'Salesforce CRM', 'api-sync', 'READ',
     'cust-0001', ARRAY['PII'],
     'Customer Onboarding',
     NOW() - INTERVAL '4 hours',
     'corr-sf-001', 'SERVICE', 'crm-sync-worker', '10.0.1.10',
     'ALLOWED', 'sha256-sf-read-001'),

    ('8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000001',
     'Salesforce CRM', 'api-sync', 'WRITE',
     'cust-0002', ARRAY['PII', 'FINANCIAL'],
     'Customer Onboarding',
     NOW() - INTERVAL '3 hours',
     'corr-sf-002', 'SERVICE', 'crm-sync-worker', '10.0.1.10',
     'ALLOWED', 'sha256-sf-write-002'),

    ('8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000001',
     'Salesforce CRM', 'api-sync', 'READ',
     'cust-0003', ARRAY['PII'],
     'Customer Onboarding',
     NOW() - INTERVAL '2 hours',
     'corr-sf-003', 'SERVICE', 'crm-sync-worker', '10.0.1.10',
     'DENIED', 'sha256-sf-read-003'),

    ('8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000002',
     'S3 Evidence Vault', 'aws-sdk', 'READ',
     NULL, ARRAY['PII', 'EMPLOYEE_DATA'],
     'Backup',
     NOW() - INTERVAL '1 hour',
     'corr-aws-001', 'SERVICE', 'backup-agent', '10.0.2.5',
     'ALLOWED', 'sha256-aws-read-001'),

    ('8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000005-0000-0000-0000-000000000004',
     'Razorpay Gateway', 'webhook', 'WRITE',
     'txn-9981', ARRAY['FINANCIAL'],
     'Customer Onboarding',
     NOW() - INTERVAL '30 minutes',
     'corr-rp-001', 'SERVICE', 'payment-worker', '10.0.3.1',
     'ALLOWED', 'sha256-rp-write-001')
ON CONFLICT DO NOTHING;
