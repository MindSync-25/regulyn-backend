-- V4__nominee_domain_enhancement.sql
-- Add claim documents, status history, rights grants, and exports tables

SET search_path TO nominee;

-- Add missing columns to nominees table
ALTER TABLE nominees
ADD COLUMN IF NOT EXISTS relationship TEXT,
ADD COLUMN IF NOT EXISTS scope TEXT,
ADD COLUMN IF NOT EXISTS verification_method TEXT,
ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS disabled_by UUID;

-- Add missing columns to nominee_claims
ALTER TABLE nominee_claims
ADD COLUMN IF NOT EXISTS approval_decision TEXT,
ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
ADD COLUMN IF NOT EXISTS evidence_id UUID;

-- Claim documents
CREATE TABLE claim_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL,
    doc_type TEXT NOT NULL,
    storage_url TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    uploaded_by UUID,
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_claim_doc FOREIGN KEY (claim_id) REFERENCES nominee_claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_documents_claim ON claim_documents(claim_id);
CREATE INDEX idx_claim_documents_type ON claim_documents(doc_type);

-- Claim status history
CREATE TABLE claim_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL,
    from_status TEXT,
    to_status TEXT NOT NULL,
    transitioned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transitioned_by UUID,
    reason TEXT,
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_claim_history FOREIGN KEY (claim_id) REFERENCES nominee_claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_history_claim ON claim_status_history(claim_id);
CREATE INDEX idx_claim_history_transitioned ON claim_status_history(transitioned_at DESC);

-- Rights grants
CREATE TABLE rights_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    nominee_id UUID NOT NULL,
    scope TEXT NOT NULL,
    status TEXT NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by UUID,
    valid_from DATE NOT NULL,
    valid_to DATE,
    claim_id UUID,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_rights_grant_nominee FOREIGN KEY (nominee_id) REFERENCES nominees(id),
    CONSTRAINT fk_rights_grant_claim FOREIGN KEY (claim_id) REFERENCES nominee_claims(id)
);

CREATE INDEX idx_rights_grants_nominee ON rights_grants(nominee_id);
CREATE INDEX idx_rights_grants_principal ON rights_grants(data_principal_id);
CREATE INDEX idx_rights_grants_status ON rights_grants(status);
CREATE INDEX idx_rights_grants_tenant ON rights_grants(tenant_id);

-- Nominee exports
CREATE TABLE nominee_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    nominee_id UUID NOT NULL,
    format TEXT NOT NULL,
    status TEXT NOT NULL,
    download_url TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    requested_by UUID,
    completed_at TIMESTAMPTZ,
    file_size_bytes BIGINT,
    expires_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_export_nominee FOREIGN KEY (nominee_id) REFERENCES nominees(id)
);

CREATE INDEX idx_nominee_exports_nominee ON nominee_exports(nominee_id);
CREATE INDEX idx_nominee_exports_status ON nominee_exports(status);
CREATE INDEX idx_nominee_exports_tenant ON nominee_exports(tenant_id);
CREATE INDEX idx_nominee_exports_requested ON nominee_exports(requested_at DESC);

-- Comments
COMMENT ON TABLE claim_documents IS 'Documents uploaded to support nominee claims';
COMMENT ON TABLE claim_status_history IS 'Audit trail of claim status transitions';
COMMENT ON TABLE rights_grants IS 'Active rights granted to nominees';
COMMENT ON TABLE nominee_exports IS 'Export requests for nominee data';
