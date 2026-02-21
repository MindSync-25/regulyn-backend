-- V5__test_data.sql
-- Test data for evidence bundles and records (local development only)

SET search_path TO evidence;

-- Insert test evidence records
INSERT INTO evidence_records (evidence_pk, tenant_id, evidence_id, evidence_type, evidence_hash, metadata, created_at, created_by) VALUES
('11111111-1111-1111-1111-111111111111', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'EVD-DSAR-2026-001', 'DSAR_RESPONSE', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2', '{"requestId": "DSAR-001", "recordCount": 15, "dataCategories": ["PERSONAL_INFO", "CONTACT_INFO", "ACTIVITY_LOGS"]}', NOW() - INTERVAL '5 days', '22222222-2222-2222-2222-222222222222'),
('33333333-3333-3333-3333-333333333333', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'EVD-DEL-2026-002', 'DELETION_PROOF', 'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4', '{"deletionRequestId": "DEL-002", "recordsDeleted": 8, "tablesAffected": ["users", "activity_logs", "consent_records"]}', NOW() - INTERVAL '3 days', '22222222-2222-2222-2222-222222222222'),
('44444444-4444-4444-4444-444444444444', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'EVD-INC-2026-003', 'INCIDENT_REPORT', 'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6', '{"incidentId": "INC-2026-003", "severity": "HIGH", "affectedUsers": 42, "reportedBy": "security@regulyn.com"}', NOW() - INTERVAL '1 day', '22222222-2222-2222-2222-222222222222'),
('55555555-5555-5555-5555-555555555555', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'EVD-AUDIT-2026-004', 'AUDIT_LOG', 'd4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1', '{"period": "2026-01", "totalEvents": 1523, "userActions": 890, "systemActions": 633}', NOW() - INTERVAL '2 hours', '22222222-2222-2222-2222-222222222222');

-- Insert test evidence bundles
INSERT INTO evidence_bundles (bundle_id, tenant_id, bundle_type, reference_type, reference_id, title, description, manifest_json, bundle_hash, status, created_at, created_by) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'DSAR', 'DSAR', 'DSAR-001', 'DSAR Bundle - John Doe Data Export', 'Complete data export for DSAR request DSAR-001 including personal info, contact details, and activity logs', 
'{"bundleId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bundleType": "DSAR", "referenceId": "DSAR-001", "items": [{"evidenceId": "EVD-DSAR-2026-001", "type": "EVIDENCE", "hash": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"}], "generatedAt": "2026-02-10T10:30:00Z", "totalItems": 1}',
'9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b', 'EXPORTED', NOW() - INTERVAL '5 days', '22222222-2222-2222-2222-222222222222'),

('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'DELETION', 'DELETION', 'DEL-002', 'Deletion Evidence - User Account Removal', 'Proof of deletion for user account removal request DEL-002 with complete deletion logs',
'{"bundleId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "bundleType": "DELETION", "referenceId": "DEL-002", "items": [{"evidenceId": "EVD-DEL-2026-002", "type": "EVIDENCE", "hash": "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"}], "generatedAt": "2026-02-12T14:20:00Z", "totalItems": 1}',
'8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c', 'EXPORTED', NOW() - INTERVAL '3 days', '22222222-2222-2222-2222-222222222222'),

('cccccccc-cccc-cccc-cccc-cccccccccccc', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'INCIDENT', 'INCIDENT', 'INC-2026-003', 'Incident Bundle - Data Breach Report', 'Evidence bundle for data breach incident INC-2026-003 including incident report and affected user list',
'{"bundleId": "cccccccc-cccc-cccc-cccc-cccccccccccc", "bundleType": "INCIDENT", "referenceId": "INC-2026-003", "items": [{"evidenceId": "EVD-INC-2026-003", "type": "EVIDENCE", "hash": "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6"}], "generatedAt": "2026-02-14T09:15:00Z", "totalItems": 1}',
'7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d', 'CREATED', NOW() - INTERVAL '1 day', '22222222-2222-2222-2222-222222222222'),

('dddddddd-dddd-dddd-dddd-dddddddddddd', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'AUDIT_EXPORT', 'PERIOD', '2026-01', 'Monthly Audit Export - January 2026', 'Complete audit trail export for January 2026 compliance reporting',
'{"bundleId": "dddddddd-dddd-dddd-dddd-dddddddddddd", "bundleType": "AUDIT_EXPORT", "referenceId": "2026-01", "items": [{"evidenceId": "EVD-AUDIT-2026-004", "type": "EVIDENCE", "hash": "d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1"}], "generatedAt": "2026-02-14T22:45:00Z", "totalItems": 1}',
'6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e', 'CREATED', NOW() - INTERVAL '2 hours', '22222222-2222-2222-2222-222222222222');

-- Insert bundle items
INSERT INTO evidence_bundle_items (item_id, tenant_id, bundle_id, item_type, evidence_id, artifact_id, item_hash, item_meta, created_at) VALUES
('aaa11111-1111-1111-1111-111111111111', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'EVIDENCE', '11111111-1111-1111-1111-111111111111', NULL, 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2', '{"size": 2048, "format": "JSON"}', NOW() - INTERVAL '5 days'),

('bbb22222-2222-2222-2222-222222222222', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'EVIDENCE', '33333333-3333-3333-3333-333333333333', NULL, 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3', '{"size": 1536, "format": "JSON"}', NOW() - INTERVAL '3 days'),

('ccc33333-3333-3333-3333-333333333333', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'EVIDENCE', '44444444-4444-4444-4444-444444444444', NULL, 'c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4', '{"size": 4096, "format": "JSON"}', NOW() - INTERVAL '1 day'),

('ddd44444-4444-4444-4444-444444444444', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 'EVIDENCE', '55555555-5555-5555-5555-555555555555', NULL, 'd4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5', '{"size": 8192, "format": "JSON"}', NOW() - INTERVAL '2 hours');

-- Insert test exports (for EXPORTED bundles)
INSERT INTO evidence_exports (export_id, tenant_id, bundle_id, export_path, export_hash, status, created_at, created_by) VALUES
('eee11111-1111-1111-1111-111111111111', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '/exports/ac0d62fc-b927-48e6-80ff-7d8acedfe054/DSAR-001.zip', '9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b', 'READY', NOW() - INTERVAL '5 days', '22222222-2222-2222-2222-222222222222'),

('fff22222-2222-2222-2222-222222222222', 'ac0d62fc-b927-48e6-80ff-7d8acedfe054', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '/exports/ac0d62fc-b927-48e6-80ff-7d8acedfe054/DEL-002.zip', '8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c6d5e4f9a8b7c', 'READY', NOW() - INTERVAL '3 days', '22222222-2222-2222-2222-222222222222');

COMMENT ON COLUMN evidence_bundles.bundle_id IS 'Test data: 4 bundles for DSAR, DELETION, INCIDENT, AUDIT_EXPORT';
COMMENT ON COLUMN evidence_records.evidence_id IS 'Test data: 4 evidence records linked to bundles';
