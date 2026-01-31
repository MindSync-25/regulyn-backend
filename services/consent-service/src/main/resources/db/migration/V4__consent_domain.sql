-- V4__consent_domain.sql
-- Create consent domain tables

SET search_path TO consent;

-- Notice templates
CREATE TABLE notice_templates (
    notice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    purpose TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    default_language TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_notice_tenant_purpose UNIQUE (tenant_id, purpose)
);

CREATE INDEX idx_notice_templates_tenant ON notice_templates(tenant_id);
CREATE INDEX idx_notice_templates_purpose ON notice_templates(tenant_id, purpose);

-- Notice versions
CREATE TABLE notice_versions (
    version_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    notice_id UUID NOT NULL,
    version_number INT NOT NULL,
    change_summary TEXT,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by UUID,
    evidence_id UUID,
    CONSTRAINT fk_notice_version_notice FOREIGN KEY (notice_id) REFERENCES notice_templates(notice_id),
    CONSTRAINT uq_notice_version UNIQUE (tenant_id, notice_id, version_number)
);

CREATE INDEX idx_notice_versions_tenant ON notice_versions(tenant_id);
CREATE INDEX idx_notice_versions_notice ON notice_versions(notice_id);
CREATE INDEX idx_notice_versions_status ON notice_versions(tenant_id, notice_id, status);
CREATE INDEX idx_notice_versions_published ON notice_versions(tenant_id, notice_id, published_at) WHERE status = 'PUBLISHED';

-- Notice language text
CREATE TABLE notice_language_text (
    language_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    language TEXT NOT NULL,
    content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_notice_language_version FOREIGN KEY (version_id) REFERENCES notice_versions(version_id),
    CONSTRAINT uq_notice_language UNIQUE (tenant_id, version_id, language)
);

CREATE INDEX idx_notice_language_tenant ON notice_language_text(tenant_id);
CREATE INDEX idx_notice_language_version ON notice_language_text(version_id);
CREATE INDEX idx_notice_language_hash ON notice_language_text(content_hash);

-- Consent receipts (append-only)
CREATE TABLE consent_receipts (
    receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    purpose TEXT NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('WIDGET', 'PORTAL')),
    status TEXT NOT NULL CHECK (status IN ('GRANTED', 'WITHDRAWN')),
    notice_id UUID NOT NULL,
    version_id UUID NOT NULL,
    version_number INT NOT NULL,
    language TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    receipt_hash TEXT NOT NULL,
    client_ref TEXT,
    idempotency_key TEXT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    withdrawn_at TIMESTAMPTZ,
    withdraw_evidence_id UUID,
    CONSTRAINT uq_consent_idempotency UNIQUE NULLS NOT DISTINCT (tenant_id, data_principal_id, purpose, idempotency_key)
);

CREATE INDEX idx_consent_receipts_tenant ON consent_receipts(tenant_id);
CREATE INDEX idx_consent_receipts_principal ON consent_receipts(tenant_id, data_principal_id, granted_at DESC);
CREATE INDEX idx_consent_receipts_purpose ON consent_receipts(tenant_id, purpose, granted_at DESC);
CREATE INDEX idx_consent_receipts_status ON consent_receipts(tenant_id, status);
CREATE INDEX idx_consent_receipts_idempotency ON consent_receipts(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Consent status history (append-only)
CREATE TABLE consent_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ DEFAULT NOW(),
    changed_by UUID,
    reason TEXT,
    CONSTRAINT fk_consent_history_receipt FOREIGN KEY (receipt_id) REFERENCES consent_receipts(receipt_id)
);

CREATE INDEX idx_consent_history_tenant ON consent_status_history(tenant_id);
CREATE INDEX idx_consent_history_receipt ON consent_status_history(receipt_id, changed_at DESC);

COMMENT ON TABLE notice_templates IS 'Notice templates for consent purposes';
COMMENT ON TABLE notice_versions IS 'Versioned notice content with publish workflow';
COMMENT ON TABLE notice_language_text IS 'Language-specific notice text content';
COMMENT ON TABLE consent_receipts IS 'Immutable consent receipts linking to notice versions';
COMMENT ON TABLE consent_status_history IS 'Audit trail of consent status changes';
