-- Create schema
CREATE SCHEMA IF NOT EXISTS scanner;

-- Set search path
SET search_path TO scanner;

-- Service metadata table
CREATE TABLE service_meta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name TEXT NOT NULL,
    version TEXT NOT NULL
);

-- Audit events table
CREATE TABLE audit_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_type TEXT,
    service TEXT NOT NULL DEFAULT 'scanner-service',
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID,
    payload_hash TEXT,
    evidence_id UUID,
    metadata JSONB
);

-- Indexes for audit_events
CREATE INDEX idx_audit_events_tenant_occurred ON audit_events(tenant_id, occurred_at);
CREATE INDEX idx_audit_events_tenant_entity ON audit_events(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_events_tenant_action ON audit_events(tenant_id, action);

-- Insert service metadata
INSERT INTO service_meta (id, tenant_id, created_at, name, version)
VALUES (gen_random_uuid(), NULL, NOW(), 'scanner-service', '0.0.1-SNAPSHOT');
