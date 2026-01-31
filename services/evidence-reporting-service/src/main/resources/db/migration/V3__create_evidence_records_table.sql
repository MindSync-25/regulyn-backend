-- V3__create_evidence_records_table.sql
CREATE TABLE evidence_records (
    evidence_pk UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    evidence_id TEXT NOT NULL UNIQUE,
    evidence_type TEXT NOT NULL,
    evidence_hash TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE INDEX idx_evidence_tenant ON evidence_records(tenant_id);
CREATE INDEX idx_evidence_id ON evidence_records(evidence_id);
CREATE INDEX idx_evidence_created ON evidence_records(created_at DESC);

COMMENT ON TABLE evidence_records IS 'Evidence records for audit trail';
