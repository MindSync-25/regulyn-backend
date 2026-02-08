-- V7__round2_notice_templates_and_dispatch.sql
-- Round 2: Notice templates, drafts, approvals, dispatch logs, and incident escalations

SET search_path TO incident;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Notice template families
CREATE TABLE notice_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_type VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT chk_notice_templates_type CHECK (template_type IN ('AUTHORITY_NOTICE','BOARD_NOTICE','IMPACTED_USER_NOTICE')),
    CONSTRAINT uq_notice_templates_tenant_type_name UNIQUE (tenant_id, template_type, name)
);

CREATE INDEX idx_notice_templates_tenant_type ON notice_templates(tenant_id, template_type);

-- Notice template versions (immutable)
CREATE TABLE notice_template_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    version INT NOT NULL,
    language VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    variables_schema JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(120) NOT NULL,
    is_retired BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_notice_template_versions_template FOREIGN KEY (template_id) REFERENCES notice_templates(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notice_template_versions_version CHECK (version > 0),
    CONSTRAINT uq_notice_template_versions UNIQUE (template_id, version, language)
);

CREATE INDEX idx_notice_template_versions_template ON notice_template_versions(tenant_id, template_id, version DESC);
CREATE INDEX idx_notice_template_versions_lang ON notice_template_versions(tenant_id, language);

-- Notice drafts per incident
CREATE TABLE notice_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    notice_type VARCHAR(40) NOT NULL,
    template_version_id UUID NOT NULL,
    language VARCHAR(16) NOT NULL,
    rendered_content TEXT NOT NULL,
    rendered_sha256 VARCHAR(64) NOT NULL,
    content_artifact_ref VARCHAR(200),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notice_drafts_incident FOREIGN KEY (incident_id) REFERENCES incident_cases(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notice_drafts_template_version FOREIGN KEY (template_version_id) REFERENCES notice_template_versions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notice_drafts_type CHECK (notice_type IN ('AUTHORITY_NOTICE','BOARD_NOTICE','IMPACTED_USER_NOTICE')),
    CONSTRAINT chk_notice_drafts_status CHECK (status IN ('DRAFT','APPROVAL_PENDING','APPROVED','REJECTED','DISPATCHED')),
    CONSTRAINT uq_notice_drafts_idempotency UNIQUE (incident_id, template_version_id, notice_type)
);

CREATE INDEX idx_notice_drafts_incident ON notice_drafts(tenant_id, incident_id);
CREATE INDEX idx_notice_drafts_status ON notice_drafts(tenant_id, status);

-- Notice approvals (maker-checker)
CREATE TABLE notice_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    approval_request_id UUID NOT NULL,
    draft_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    requested_by VARCHAR(120) NOT NULL,
    requested_comment TEXT,
    decided_at TIMESTAMPTZ,
    decided_by VARCHAR(120),
    decided_comment TEXT,
    CONSTRAINT fk_notice_approvals_draft FOREIGN KEY (draft_id) REFERENCES notice_drafts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notice_approvals_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED')),
    CONSTRAINT uq_notice_approvals_request UNIQUE (approval_request_id)
);

CREATE INDEX idx_notice_approvals_draft ON notice_approvals(tenant_id, draft_id);

-- Notice dispatch logs
CREATE TABLE notice_dispatch_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    draft_id UUID NOT NULL,
    recipient_type VARCHAR(24) NOT NULL,
    recipient_identifier VARCHAR(256) NOT NULL,
    channel VARCHAR(24) NOT NULL DEFAULT 'EMAIL',
    status VARCHAR(32) NOT NULL,
    notification_request_id VARCHAR(120),
    provider_message_id VARCHAR(120),
    dispatch_payload_sha256 VARCHAR(64) NOT NULL,
    receipt_ref VARCHAR(200),
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_status_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notice_dispatch_logs_draft FOREIGN KEY (draft_id) REFERENCES notice_drafts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notice_dispatch_logs_recipient CHECK (recipient_type IN ('AUTHORITY','BOARD','IMPACTED_USER')),
    CONSTRAINT chk_notice_dispatch_logs_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT chk_notice_dispatch_logs_status CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED_TERMINAL')),
    CONSTRAINT uq_notice_dispatch_logs_idempotency UNIQUE (draft_id, recipient_identifier, channel)
);

CREATE INDEX idx_notice_dispatch_draft ON notice_dispatch_logs(tenant_id, draft_id);
CREATE INDEX idx_notice_dispatch_status ON notice_dispatch_logs(tenant_id, status);
CREATE INDEX idx_notice_dispatch_request ON notice_dispatch_logs(tenant_id, notification_request_id);

-- Incident escalations
CREATE TABLE incident_escalations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    threshold_hours INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    notification_request_id VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at TIMESTAMPTZ,
    CONSTRAINT fk_incident_escalations_incident FOREIGN KEY (incident_id) REFERENCES incident_cases(id) ON DELETE RESTRICT,
    CONSTRAINT chk_incident_escalations_threshold CHECK (threshold_hours > 0),
    CONSTRAINT chk_incident_escalations_status CHECK (status IN ('CREATED','NOTIFIED')),
    CONSTRAINT uq_incident_escalations_idempotency UNIQUE (incident_id, threshold_hours)
);

CREATE INDEX idx_incident_escalations_incident ON incident_escalations(tenant_id, incident_id);

-- Immutability trigger for notice_template_versions
CREATE OR REPLACE FUNCTION incident.prevent_notice_template_version_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'notice_template_versions are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_notice_template_versions_immutable ON notice_template_versions;

CREATE TRIGGER trg_notice_template_versions_immutable
BEFORE UPDATE OR DELETE ON notice_template_versions
FOR EACH ROW EXECUTE FUNCTION incident.prevent_notice_template_version_mutation();
