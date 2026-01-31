-- Create evidence schema
CREATE SCHEMA IF NOT EXISTS evidence;

-- Set search path for this migration
SET search_path TO evidence;

-- Service metadata table
CREATE TABLE service_meta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name TEXT NOT NULL,
    version TEXT NOT NULL
);

-- Evidence records table (metadata about evidence)
CREATE TABLE evidence_records (
    evidence_pk UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    evidence_id TEXT NOT NULL UNIQUE,
    evidence_type TEXT NOT NULL,
    evidence_hash TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE INDEX idx_evidence_tenant ON evidence_records(tenant_id);
CREATE INDEX idx_evidence_id ON evidence_records(evidence_id);
CREATE INDEX idx_evidence_created ON evidence_records(created_at DESC);

-- Evidence artifacts table (files attached to evidence)
CREATE TABLE evidence_artifacts (
    artifact_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id TEXT NOT NULL REFERENCES evidence_records(evidence_id),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    filename TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT NOT NULL,
    artifact_ref TEXT NOT NULL,
    artifact_hash TEXT NOT NULL
);

CREATE INDEX idx_evidence_artifacts_evidence ON evidence_artifacts(evidence_id);
CREATE INDEX idx_evidence_artifacts_tenant ON evidence_artifacts(tenant_id);

-- Append-only audit events table
CREATE TABLE audit_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_type TEXT NOT NULL,
    service TEXT NOT NULL DEFAULT 'evidence-reporting-service',
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    evidence_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB
);

-- Indexes for audit_events
CREATE INDEX idx_audit_events_tenant_occurred ON audit_events(tenant_id, occurred_at);
CREATE INDEX idx_audit_events_tenant_entity ON audit_events(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_events_tenant_action ON audit_events(tenant_id, action);

-- Insert service metadata
INSERT INTO service_meta (name, version) VALUES ('evidence-reporting-service', '0.0.1-SNAPSHOT');
