-- Create nominee schema
CREATE SCHEMA IF NOT EXISTS nominee;

-- Set search path for this migration
SET search_path TO nominee;

-- Service metadata table
CREATE TABLE service_meta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name TEXT NOT NULL,
    version TEXT NOT NULL
);

-- Append-only audit events table
CREATE TABLE audit_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_type TEXT NOT NULL,
    service TEXT NOT NULL DEFAULT 'nominee-service',
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
INSERT INTO service_meta (name, version) VALUES ('nominee-service', '0.0.1-SNAPSHOT');
