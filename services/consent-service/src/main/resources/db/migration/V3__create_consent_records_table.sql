-- V3__create_consent_records_table.sql
CREATE TABLE consent_records (
    consent_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    receipt_id TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL,
    purpose TEXT NOT NULL,
    language TEXT NOT NULL,
    notice_hash TEXT NOT NULL,
    source TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE INDEX idx_consent_tenant_user ON consent_records(tenant_id, user_id);
CREATE INDEX idx_consent_receipt ON consent_records(receipt_id);
CREATE INDEX idx_consent_created ON consent_records(created_at DESC);

COMMENT ON TABLE consent_records IS 'Consent records with hashed notice text';
COMMENT ON COLUMN consent_records.notice_hash IS 'SHA-256 hash of the full notice text';
