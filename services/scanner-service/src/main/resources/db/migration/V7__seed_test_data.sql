-- V7: Seed test data for scanner-service
-- Tenant: 8d047e8d-b550-4dff-8308-01ea1b4676ae
-- Cross-references ROPA system UUID '00000001-0000-0000-0000-000000000001' (Salesforce CRM)

-- ─────────────────────────────────────────────────────────────────────────────
-- Scan Sources
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO scanner.scan_sources
    (source_id, tenant_id, source_name, system_id, source_type,
     status, base_url, auth_type)
VALUES
    ('00000006-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Main Customer Portal',
     '00000001-0000-0000-0000-000000000001',
     'WEB', 'ACTIVE',
     'https://app.regulyn.com',
     'NONE'),

    ('00000006-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Admin & Ops Portal',
     '00000001-0000-0000-0000-000000000001',
     'WEB', 'ACTIVE',
     'https://admin.regulyn.com',
     'NONE'),

    ('00000006-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     'Public Marketing Site',
     '00000001-0000-0000-0000-000000000003',
     'WEB', 'DISABLED',
     'https://www.regulyn.com',
     'NONE')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Scan Runs
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO scanner.scan_runs
    (run_id, tenant_id, source_id, scan_mode, status,
     queued_at, started_at, finished_at, findings_count, request_ref)
VALUES
    -- Completed full scan on Main Portal (2 days ago)
    ('00000007-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     'FULL', 'COMPLETED',
     NOW() - INTERVAL '2 days',
     NOW() - INTERVAL '2 days' + INTERVAL '1 minute',
     NOW() - INTERVAL '2 days' + INTERVAL '47 minutes',
     3, 'scheduled-daily'),

    -- Completed incremental on Main Portal (yesterday)
    ('00000007-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     'INCREMENTAL', 'COMPLETED',
     NOW() - INTERVAL '1 day',
     NOW() - INTERVAL '1 day' + INTERVAL '30 seconds',
     NOW() - INTERVAL '1 day' + INTERVAL '12 minutes',
     0, 'scheduled-daily'),

    -- Currently running on Main Portal
    ('00000007-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     'INCREMENTAL', 'RUNNING',
     NOW() - INTERVAL '8 minutes',
     NOW() - INTERVAL '7 minutes',
     NULL,
     0, 'scheduled-daily'),

    -- Queued on Admin Portal
    ('00000007-0000-0000-0000-000000000004',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000002',
     'FULL', 'QUEUED',
     NOW() - INTERVAL '2 minutes',
     NULL, NULL,
     0, 'manual-trigger'),

    -- Failed run on Admin Portal (3 days ago)
    ('00000007-0000-0000-0000-000000000005',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000002',
     'FULL', 'FAILED',
     NOW() - INTERVAL '3 days',
     NOW() - INTERVAL '3 days' + INTERVAL '1 minute',
     NOW() - INTERVAL '3 days' + INTERVAL '3 minutes',
     0, 'scheduled-daily')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Scan Findings  (from the completed run 1)
-- finding_fingerprint: VARCHAR(64) after V6 migration
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO scanner.scan_findings
    (finding_id, tenant_id, run_id, finding_type, entity_type,
     subject_id, field_name, data_category, risk_level, confidence,
     finding_fingerprint, finding_fingerprint_version, normalized_subject)
VALUES
    -- Exposed PII in signup form
    ('00000008-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000007-0000-0000-0000-000000000001',
     'EXPOSED_PII', 'FORM',
     NULL, 'email_input', 'PII', 'HIGH', 90,
     'fp-exposed-pii-signup-form-email-v1-regulyn-portal',
     1, 'signup-form'),

    -- Long-lived analytics cookie (2-year expiry, no consent gate)
    ('00000008-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000007-0000-0000-0000-000000000001',
     'EXCESSIVE_RETENTION', 'COOKIE',
     NULL, '_ga', 'DEVICE', 'MED', 75,
     'fp-excessive-retention-cookie-ga-v1-regulyn-portal',
     1, 'google-analytics-cookie'),

    -- Facebook Pixel firing without consent
    ('00000008-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000007-0000-0000-0000-000000000001',
     'MISSING_CONSENT', 'TRACKER',
     NULL, 'fbq', 'PII', 'HIGH', 88,
     'fp-missing-consent-tracker-fbq-v1-regulyn-portal',
     1, 'facebook-pixel')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Remediation Tasks  (linked to findings above)
-- Note: unique constraint on (tenant_id, finding_fingerprint) for OPEN/IN_PROGRESS
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO scanner.remediation_tasks
    (task_id, tenant_id, source_id, run_id, finding_pk,
     finding_fingerprint, title, severity,
     owner_email, due_date, status)
VALUES
    -- Task for exposed PII finding (OPEN)
    ('0000000d-0000-0000-0000-000000000001',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     '00000007-0000-0000-0000-000000000001',
     '00000008-0000-0000-0000-000000000001',
     'fp-exposed-pii-signup-form-email-v1-regulyn-portal',
     'Remove PII from exposed signup form — mask email field server-side',
     'HIGH',
     'privacy@regulyn.com',
     CURRENT_DATE + INTERVAL '7 days',
     'OPEN'),

    -- Task for Facebook Pixel consent gap (IN_PROGRESS)
    ('0000000d-0000-0000-0000-000000000002',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     '00000007-0000-0000-0000-000000000001',
     '00000008-0000-0000-0000-000000000003',
     'fp-missing-consent-tracker-fbq-v1-regulyn-portal',
     'Gate Facebook Pixel behind consent banner — block fbq() until ACCEPTED',
     'HIGH',
     'engineering@regulyn.com',
     CURRENT_DATE + INTERVAL '14 days',
     'IN_PROGRESS'),

    -- Task for long-lived cookie (CLOSED — already fixed)
    ('0000000d-0000-0000-0000-000000000003',
     '8d047e8d-b550-4dff-8308-01ea1b4676ae',
     '00000006-0000-0000-0000-000000000001',
     '00000007-0000-0000-0000-000000000001',
     '00000008-0000-0000-0000-000000000002',
     'fp-excessive-retention-cookie-ga-v1-regulyn-portal-closed',
     'Reduce _ga cookie lifetime from 2 years to 13 months (GA4 default)',
     'MED',
     'engineering@regulyn.com',
     CURRENT_DATE - INTERVAL '5 days',
     'CLOSED')
ON CONFLICT DO NOTHING;
