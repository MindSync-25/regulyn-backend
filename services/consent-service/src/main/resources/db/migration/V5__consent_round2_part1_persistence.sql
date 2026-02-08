-- V5__consent_round2_part1_persistence.sql
-- Round 2 Part 1: persistence structures only

SET search_path TO consent;

-- Purpose versions
CREATE TABLE purpose_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    notice_id UUID NOT NULL,
    notice_version_id UUID NULL,
    purpose_key VARCHAR(200) NOT NULL,
    version_num INT NOT NULL,
    scope_json JSONB NOT NULL,
    scope_hash_sha256 VARCHAR(64) NOT NULL,
    created_by_actor_id UUID NULL,
    created_by_actor_type VARCHAR(40) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_purpose_versions_notice FOREIGN KEY (notice_id) REFERENCES notice_templates(notice_id),
    CONSTRAINT fk_purpose_versions_notice_version FOREIGN KEY (notice_version_id) REFERENCES notice_versions(version_id),
    CONSTRAINT uq_purpose_versions UNIQUE (tenant_id, notice_id, purpose_key, version_num)
);

CREATE UNIQUE INDEX ux_purpose_versions_notice_version
    ON purpose_versions(tenant_id, notice_version_id, purpose_key)
    WHERE notice_version_id IS NOT NULL;

CREATE INDEX idx_purpose_versions_tenant_notice_version
    ON purpose_versions(tenant_id, notice_id, purpose_key, version_num DESC);

CREATE INDEX idx_purpose_versions_scope_hash
    ON purpose_versions(tenant_id, scope_hash_sha256);

-- Purpose version history
CREATE TABLE purpose_version_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    notice_id UUID NOT NULL,
    purpose_key VARCHAR(200) NOT NULL,
    from_purpose_version_id UUID NULL,
    to_purpose_version_id UUID NOT NULL,
    change_summary VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_purpose_version_history_tenant
    ON purpose_version_history(tenant_id, notice_id, purpose_key, created_at DESC);

-- Consent receipts: round 2 linkage columns
ALTER TABLE consent_receipts
    ADD COLUMN purpose_version_id UUID NULL,
    ADD COLUMN language_code VARCHAR(12) NULL,
    ADD COLUMN notice_language_text_id UUID NULL,
    ADD COLUMN notice_content_hash_sha256 VARCHAR(64) NULL,
    ADD COLUMN evidence_artifact_id UUID NULL;

ALTER TABLE consent_receipts
    ADD CONSTRAINT fk_consent_receipts_purpose_version
    FOREIGN KEY (purpose_version_id) REFERENCES purpose_versions(id);

ALTER TABLE consent_receipts
    ADD CONSTRAINT fk_consent_receipts_notice_language
    FOREIGN KEY (notice_language_text_id) REFERENCES notice_language_text(language_id);

CREATE INDEX idx_consent_receipts_principal_purpose_version
    ON consent_receipts(tenant_id, data_principal_id, purpose_version_id);

CREATE INDEX idx_consent_receipts_principal_purpose_status
    ON consent_receipts(tenant_id, data_principal_id, purpose, status);

CREATE INDEX idx_consent_receipts_notice_version_language
    ON consent_receipts(tenant_id, version_id, language_code);

CREATE UNIQUE INDEX ux_consent_receipts_round2_idempotency
    ON consent_receipts(tenant_id, data_principal_id, version_id, language_code, purpose_version_id)
    WHERE purpose_version_id IS NOT NULL AND language_code IS NOT NULL;

-- Communication consent ledger
CREATE TABLE communication_consent_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    source VARCHAR(80) NOT NULL,
    actor_id UUID NULL,
    actor_type VARCHAR(40) NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    effective_time_bucket TIMESTAMPTZ NOT NULL,
    language_code VARCHAR(12) NULL,
    consent_text_hash_sha256 VARCHAR(64) NOT NULL,
    notice_language_text_id UUID NULL,
    evidence_artifact_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_comm_consent_notice_language FOREIGN KEY (notice_language_text_id) REFERENCES notice_language_text(language_id),
    CONSTRAINT uq_comm_consent_idempotency UNIQUE (tenant_id, data_principal_id, channel, effective_time_bucket, state)
);

CREATE INDEX idx_comm_consent_principal_channel
    ON communication_consent_ledger(tenant_id, data_principal_id, channel, effective_at DESC);

CREATE INDEX idx_comm_consent_channel
    ON communication_consent_ledger(tenant_id, channel, effective_at DESC);

-- Consent invalidations
CREATE TABLE consent_invalidations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    consent_receipt_id UUID NOT NULL,
    invalidation_reason VARCHAR(120) NOT NULL,
    invalidated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    triggered_by_purpose_version_id UUID NULL,
    audit_event_id UUID NULL,
    outbox_event_id UUID NULL,
    CONSTRAINT fk_consent_invalidations_receipt FOREIGN KEY (consent_receipt_id) REFERENCES consent_receipts(receipt_id),
    CONSTRAINT fk_consent_invalidations_purpose_version FOREIGN KEY (triggered_by_purpose_version_id) REFERENCES purpose_versions(id),
    CONSTRAINT uq_consent_invalidations UNIQUE (tenant_id, consent_receipt_id)
);

CREATE INDEX idx_consent_invalidations_principal
    ON consent_invalidations(tenant_id, data_principal_id, invalidated_at DESC);

-- Reconsent requirements
CREATE TABLE reconsent_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_principal_id UUID NOT NULL,
    notice_id UUID NOT NULL,
    purpose_key VARCHAR(200) NOT NULL,
    required_purpose_version_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    satisfied_at TIMESTAMPTZ NULL,
    satisfied_by_consent_receipt_id UUID NULL,
    CONSTRAINT fk_reconsent_notice FOREIGN KEY (notice_id) REFERENCES notice_templates(notice_id),
    CONSTRAINT fk_reconsent_required_purpose_version FOREIGN KEY (required_purpose_version_id) REFERENCES purpose_versions(id),
    CONSTRAINT fk_reconsent_satisfied_receipt FOREIGN KEY (satisfied_by_consent_receipt_id) REFERENCES consent_receipts(receipt_id),
    CONSTRAINT uq_reconsent_requirements UNIQUE (tenant_id, data_principal_id, required_purpose_version_id)
);

CREATE INDEX idx_reconsent_requirements_status
    ON reconsent_requirements(tenant_id, data_principal_id, status);

CREATE INDEX idx_reconsent_requirements_purpose_version
    ON reconsent_requirements(tenant_id, required_purpose_version_id);
