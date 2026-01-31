-- V3__create_retention_deletion_tables.sql
-- Create retention and deletion workflow tables

SET search_path TO deletion;

-- Retention rules configuration
CREATE TABLE retention_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type TEXT NOT NULL,
    retention_days INT NOT NULL,
    action TEXT NOT NULL,
    enabled BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_retention_rules_tenant ON retention_rules(tenant_id);
CREATE INDEX idx_retention_rules_enabled ON retention_rules(enabled) WHERE enabled = true;

-- Deletion requests
CREATE TABLE deletion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    subject_type TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    due_at TIMESTAMPTZ NOT NULL,
    assigned_to UUID,
    requires_approval BOOLEAN DEFAULT true,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    proof_required BOOLEAN DEFAULT true,
    closed_at TIMESTAMPTZ,
    idempotency_key TEXT,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_deletion_tenant_status ON deletion_requests(tenant_id, status);
CREATE INDEX idx_deletion_subject ON deletion_requests(subject_id);
CREATE INDEX idx_deletion_due_at ON deletion_requests(due_at) WHERE closed_at IS NULL;
CREATE INDEX idx_deletion_assigned ON deletion_requests(assigned_to) WHERE closed_at IS NULL;
CREATE UNIQUE INDEX idx_deletion_idempotency ON deletion_requests(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Deletion proofs
CREATE TABLE deletion_proofs (
    proof_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    filename TEXT NOT NULL,
    artifact_ref TEXT NOT NULL,
    artifact_hash TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ DEFAULT NOW(),
    uploaded_by UUID,
    CONSTRAINT fk_deletion_proof FOREIGN KEY (deletion_id) REFERENCES deletion_requests(id)
);

CREATE INDEX idx_deletion_proofs_deletion ON deletion_proofs(deletion_id);
CREATE INDEX idx_deletion_proofs_tenant ON deletion_proofs(tenant_id);

-- Deletion status history
CREATE TABLE deletion_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ DEFAULT NOW(),
    changed_by UUID,
    reason TEXT,
    CONSTRAINT fk_deletion_history FOREIGN KEY (deletion_id) REFERENCES deletion_requests(id)
);

CREATE INDEX idx_deletion_history_deletion ON deletion_status_history(deletion_id, changed_at DESC);
CREATE INDEX idx_deletion_history_tenant ON deletion_status_history(tenant_id);

COMMENT ON TABLE retention_rules IS 'Tenant-specific data retention policies';
COMMENT ON TABLE deletion_requests IS 'Data deletion workflow requests';
COMMENT ON TABLE deletion_proofs IS 'Proof of deletion artifacts';
COMMENT ON TABLE deletion_status_history IS 'Append-only audit log of deletion status transitions';
