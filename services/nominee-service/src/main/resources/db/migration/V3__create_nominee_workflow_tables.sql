-- V3__create_nominee_workflow_tables.sql
-- Create nominee and claim workflow tables

SET search_path TO nominee;

-- Nominees
CREATE TABLE nominees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    nominee_name TEXT NOT NULL,
    nominee_contact TEXT,
    status TEXT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_nominees_tenant_user ON nominees(tenant_id, data_principal_id);
CREATE INDEX idx_nominees_status ON nominees(status);

-- Nominee claims
CREATE TABLE nominee_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    nominee_id UUID NOT NULL,
    claim_type TEXT NOT NULL,
    status TEXT NOT NULL,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    documents_bundle_id UUID,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_nominee_claim FOREIGN KEY (nominee_id) REFERENCES nominees(id)
);

CREATE INDEX idx_nominee_claims_nominee ON nominee_claims(nominee_id);
CREATE INDEX idx_nominee_claims_status ON nominee_claims(status);
CREATE INDEX idx_nominee_claims_tenant ON nominee_claims(tenant_id);

COMMENT ON TABLE nominees IS 'Registered nominees for data access rights transfer';
COMMENT ON TABLE nominee_claims IS 'Claims submitted by nominees for rights transfer';
