-- V5__nominee_documents.sql
-- Nominee document ledger + optional verification requirements + claim bridge

SET search_path TO nominee;

-- Nominee documents ledger
CREATE TABLE nominee_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    nominee_id UUID NOT NULL,
    claim_id UUID NULL,
    verification_step VARCHAR(64) NOT NULL,
    artifact_ref VARCHAR(256) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    uploaded_by VARCHAR(128) NULL,
    idempotency_key VARCHAR(128) NULL,
    doc_notes TEXT NULL,
    CONSTRAINT ck_nominee_documents_size_bytes CHECK (size_bytes >= 0),
    CONSTRAINT uq_nominee_documents_tenant_nominee_hash UNIQUE (tenant_id, nominee_id, sha256_hash)
);

CREATE INDEX idx_nominee_documents_tenant_nominee_uploaded
    ON nominee_documents(tenant_id, nominee_id, uploaded_at DESC);

CREATE INDEX idx_nominee_documents_tenant_nominee_step
    ON nominee_documents(tenant_id, nominee_id, verification_step);

CREATE INDEX idx_nominee_documents_tenant_claim
    ON nominee_documents(tenant_id, claim_id)
    WHERE claim_id IS NOT NULL;

CREATE INDEX idx_nominee_documents_tenant_sha256
    ON nominee_documents(tenant_id, sha256_hash);

-- Nominee verification requirements (tenant scoped)
CREATE TABLE nominee_verification_requirements (
    tenant_id UUID NOT NULL,
    verification_step VARCHAR(64) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    min_docs INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NULL,
    updated_at TIMESTAMPTZ NULL,
    updated_by VARCHAR(128) NULL,
    CONSTRAINT ck_nominee_verification_requirements_min_docs CHECK (min_docs >= 0),
    CONSTRAINT pk_nominee_verification_requirements PRIMARY KEY (tenant_id, verification_step)
);

CREATE INDEX idx_nominee_verification_requirements_tenant_active
    ON nominee_verification_requirements(tenant_id, active);

-- Optional bridge from claim_documents -> nominee_documents
ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS nominee_document_id UUID NULL;

CREATE INDEX IF NOT EXISTS idx_claim_documents_nominee_document
    ON claim_documents(nominee_document_id);
