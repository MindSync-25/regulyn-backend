-- V3__create_guardian_workflow_tables.sql
-- Create children and guardian workflow tables

SET search_path TO guardian;

-- Child profiles (is_child computed in application layer)
CREATE TABLE child_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    dob DATE NOT NULL,
    is_child BOOLEAN,
    registered_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_child_profiles_tenant ON child_profiles(tenant_id);
CREATE INDEX idx_child_profiles_user ON child_profiles(user_id);
CREATE INDEX idx_child_profiles_is_child ON child_profiles(is_child) WHERE is_child = true;

-- Guardian verifications
CREATE TABLE guardian_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    child_profile_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    status TEXT NOT NULL,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    verified_at TIMESTAMPTZ,
    evidence_artifact_ref TEXT,
    evidence_artifact_hash TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_guardian_verification_child FOREIGN KEY (child_profile_id) REFERENCES child_profiles(id)
);

CREATE INDEX idx_guardian_verifications_child ON guardian_verifications(child_profile_id);
CREATE INDEX idx_guardian_verifications_status ON guardian_verifications(status);
CREATE INDEX idx_guardian_verifications_guardian ON guardian_verifications(guardian_id);

-- Guardian consents
CREATE TABLE guardian_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    child_profile_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    consent_receipt_id UUID,
    signed_artifact_ref TEXT,
    signed_artifact_hash TEXT,
    signed_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    granted_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}',
    CONSTRAINT fk_guardian_consent_child FOREIGN KEY (child_profile_id) REFERENCES child_profiles(id)
);

CREATE INDEX idx_guardian_consents_child ON guardian_consents(child_profile_id);
CREATE INDEX idx_guardian_consents_status ON guardian_consents(status);
CREATE INDEX idx_guardian_consents_guardian ON guardian_consents(guardian_id);

COMMENT ON TABLE child_profiles IS 'Profiles for children requiring guardian consent';
COMMENT ON TABLE guardian_verifications IS 'Guardian relationship verification records';
COMMENT ON TABLE guardian_consents IS 'Guardian consent records for children';
