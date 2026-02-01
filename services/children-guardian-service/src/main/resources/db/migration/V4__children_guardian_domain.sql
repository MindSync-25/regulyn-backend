-- V4__children_guardian_domain.sql
-- Children and Guardian domain tables for DPDP-compliant age-gating + consent workflow

-- Create schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS children;

-- 1) Children table
CREATE TABLE IF NOT EXISTS children.children (
    child_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    child_ref TEXT NOT NULL,
    full_name TEXT NOT NULL,
    date_of_birth DATE NOT NULL,
    country TEXT,
    status TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_children_tenant_ref UNIQUE (tenant_id, child_ref)
);

CREATE INDEX IF NOT EXISTS idx_children_tenant_status ON children.children(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_children_tenant_dob ON children.children(tenant_id, date_of_birth);

-- 2) Guardians table
CREATE TABLE IF NOT EXISTS children.guardians (
    guardian_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    child_id UUID NOT NULL,
    guardian_name TEXT NOT NULL,
    guardian_email TEXT,
    guardian_phone TEXT,
    relationship TEXT NOT NULL,
    status TEXT NOT NULL,
    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guardians_child FOREIGN KEY (child_id) REFERENCES children.children(child_id)
);

CREATE INDEX IF NOT EXISTS idx_guardians_tenant_child ON children.guardians(tenant_id, child_id);
CREATE INDEX IF NOT EXISTS idx_guardians_tenant_status ON children.guardians(tenant_id, status);

-- 3) Guardian Consents table
CREATE TABLE IF NOT EXISTS children.guardian_consents (
    consent_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    child_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    purpose_key TEXT NOT NULL,
    consent_scope TEXT NOT NULL,
    status TEXT NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    idempotency_key TEXT,
    valid_from DATE,
    valid_to DATE,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    revoke_reason TEXT,
    closed_at TIMESTAMPTZ,
    closure_notes TEXT,
    evidence_bundle_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consents_child FOREIGN KEY (child_id) REFERENCES children.children(child_id),
    CONSTRAINT fk_consents_guardian FOREIGN KEY (guardian_id) REFERENCES children.guardians(guardian_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_consents_idempotency 
    ON children.guardian_consents(tenant_id, child_id, guardian_id, purpose_key, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_consents_tenant_status_created ON children.guardian_consents(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_consents_tenant_child ON children.guardian_consents(tenant_id, child_id);
CREATE INDEX IF NOT EXISTS idx_consents_tenant_guardian ON children.guardian_consents(tenant_id, guardian_id);

-- 4) Consent Signed Artifacts table
CREATE TABLE IF NOT EXISTS children.consent_signed_artifacts (
    artifact_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    consent_id UUID NOT NULL,
    artifact_ref TEXT NOT NULL,
    artifact_type TEXT NOT NULL,
    content_hash TEXT,
    signed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_artifacts_consent FOREIGN KEY (consent_id) REFERENCES children.guardian_consents(consent_id)
);

CREATE INDEX IF NOT EXISTS idx_artifacts_tenant_consent ON children.consent_signed_artifacts(tenant_id, consent_id);

-- 5) Consent Status History (append-only audit trail)
CREATE TABLE IF NOT EXISTS children.consent_status_history (
    history_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    consent_id UUID NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by UUID,
    reason TEXT,
    CONSTRAINT fk_history_consent FOREIGN KEY (consent_id) REFERENCES children.guardian_consents(consent_id)
);

CREATE INDEX IF NOT EXISTS idx_history_tenant_consent_changed ON children.consent_status_history(tenant_id, consent_id, changed_at);

-- 6) Children Exports table
CREATE TABLE IF NOT EXISTS children.children_exports (
    export_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exports_tenant_created ON children.children_exports(tenant_id, created_at);
