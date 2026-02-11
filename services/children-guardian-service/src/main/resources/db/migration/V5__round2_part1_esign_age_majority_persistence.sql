-- V5__round2_part1_esign_age_majority_persistence.sql
-- Round 2 Part 1: persistence for age thresholds, eSign, majority transitions, evidence exports

SET search_path TO children;

-- A) Age threshold rules
CREATE TABLE IF NOT EXISTS children.age_threshold_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    region_country_code VARCHAR(2) NOT NULL,
    region_state_code VARCHAR(10),
    threshold_age_years SMALLINT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    min_legal_age_years SMALLINT NOT NULL DEFAULT 13,
    max_legal_age_years SMALLINT NOT NULL DEFAULT 18,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    CONSTRAINT chk_age_threshold_years
        CHECK (threshold_age_years BETWEEN min_legal_age_years AND max_legal_age_years)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_age_threshold_rules_region
    ON children.age_threshold_rules(tenant_id, region_country_code, COALESCE(region_state_code, ''));

CREATE UNIQUE INDEX IF NOT EXISTS idx_age_threshold_rules_default
    ON children.age_threshold_rules(tenant_id)
    WHERE is_default = true;

CREATE INDEX IF NOT EXISTS idx_age_threshold_rules_tenant
    ON children.age_threshold_rules(tenant_id);

CREATE INDEX IF NOT EXISTS idx_age_threshold_rules_region_lookup
    ON children.age_threshold_rules(tenant_id, region_country_code, region_state_code);

-- B) eSign requests
CREATE TABLE IF NOT EXISTS children.esign_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    child_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    consent_id UUID,
    doc_type VARCHAR(50) NOT NULL,
    doc_version INTEGER NOT NULL DEFAULT 1,
    provider VARCHAR(30) NOT NULL,
    provider_envelope_id VARCHAR(120) NOT NULL,
    signing_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_payload_sha256 VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_esign_requests_consent
        FOREIGN KEY (consent_id) REFERENCES children.guardian_consents(consent_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_esign_requests_idempotency
    ON children.esign_requests(tenant_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS idx_esign_requests_provider_envelope
    ON children.esign_requests(tenant_id, provider, provider_envelope_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_esign_requests_idempotent_form
    ON children.esign_requests(tenant_id, child_id, guardian_id, doc_type, doc_version);

CREATE INDEX IF NOT EXISTS idx_esign_requests_tenant_child
    ON children.esign_requests(tenant_id, child_id);

CREATE INDEX IF NOT EXISTS idx_esign_requests_tenant_guardian
    ON children.esign_requests(tenant_id, guardian_id);

CREATE INDEX IF NOT EXISTS idx_esign_requests_tenant_status
    ON children.esign_requests(tenant_id, status);

-- C) eSign webhook events
CREATE TABLE IF NOT EXISTS children.esign_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_envelope_id VARCHAR(120) NOT NULL,
    provider_event_id VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_payload TEXT NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    signature_header TEXT,
    signature_verification_status VARCHAR(30) NOT NULL,
    signature_verification_error TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_esign_webhook_events_idempotency
    ON children.esign_webhook_events(tenant_id, provider, provider_envelope_id, provider_event_id);

CREATE INDEX IF NOT EXISTS idx_esign_webhook_events_envelope
    ON children.esign_webhook_events(tenant_id, provider, provider_envelope_id);

CREATE INDEX IF NOT EXISTS idx_esign_webhook_events_received
    ON children.esign_webhook_events(tenant_id, received_at);

-- D) Alter consent signed artifacts
ALTER TABLE children.consent_signed_artifacts
    ADD COLUMN IF NOT EXISTS esign_request_id UUID,
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30),
    ADD COLUMN IF NOT EXISTS provider_envelope_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS signed_payload_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS artifact_mime VARCHAR(100),
    ADD COLUMN IF NOT EXISTS artifact_stored_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS signature_verified BOOLEAN,
    ADD COLUMN IF NOT EXISTS signature_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS signature_verification_error TEXT;

ALTER TABLE children.consent_signed_artifacts
    ADD CONSTRAINT fk_consent_signed_artifacts_esign_request
        FOREIGN KEY (esign_request_id) REFERENCES children.esign_requests(id);

CREATE INDEX IF NOT EXISTS idx_consent_signed_artifacts_tenant_esign
    ON children.consent_signed_artifacts(tenant_id, esign_request_id);

-- E) Alter guardian consents
ALTER TABLE children.guardian_consents
    ADD COLUMN IF NOT EXISTS esign_request_id UUID,
    ADD COLUMN IF NOT EXISTS signed_artifact_id UUID,
    ADD COLUMN IF NOT EXISTS majority_date DATE,
    ADD COLUMN IF NOT EXISTS region_country_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS region_state_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS threshold_age_years SMALLINT;

ALTER TABLE children.guardian_consents
    ADD CONSTRAINT fk_guardian_consents_esign_request
        FOREIGN KEY (esign_request_id) REFERENCES children.esign_requests(id);

ALTER TABLE children.guardian_consents
    ADD CONSTRAINT fk_guardian_consents_signed_artifact
        FOREIGN KEY (signed_artifact_id) REFERENCES children.consent_signed_artifacts(artifact_id);

CREATE INDEX IF NOT EXISTS idx_guardian_consents_tenant_child
    ON children.guardian_consents(tenant_id, child_id);

CREATE INDEX IF NOT EXISTS idx_guardian_consents_tenant_guardian
    ON children.guardian_consents(tenant_id, guardian_id);

CREATE INDEX IF NOT EXISTS idx_guardian_consents_tenant_esign
    ON children.guardian_consents(tenant_id, esign_request_id);

-- F) Majority transitions
CREATE TABLE IF NOT EXISTS children.child_majority_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    child_id UUID NOT NULL,
    dob DATE NOT NULL,
    region_country_code VARCHAR(2) NOT NULL,
    region_state_code VARCHAR(10),
    threshold_age_years SMALLINT NOT NULL,
    majority_date DATE NOT NULL,
    transition_status VARCHAR(40) NOT NULL,
    last_evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_child_majority_transitions_unique
    ON children.child_majority_transitions(tenant_id, child_id, majority_date);

CREATE INDEX IF NOT EXISTS idx_child_majority_transitions_status
    ON children.child_majority_transitions(tenant_id, majority_date, transition_status);

-- G) Children evidence exports
CREATE TABLE IF NOT EXISTS children.children_evidence_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    child_id UUID NOT NULL,
    export_scope VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    evidence_bundle_ref VARCHAR(200),
    evidence_bundle_sha256 VARCHAR(64),
    requested_by UUID,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_children_evidence_exports_idempotency
    ON children.children_evidence_exports(tenant_id, child_id, export_scope, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_children_evidence_exports_child
    ON children.children_evidence_exports(tenant_id, child_id);

CREATE INDEX IF NOT EXISTS idx_children_evidence_exports_status
    ON children.children_evidence_exports(tenant_id, status);
