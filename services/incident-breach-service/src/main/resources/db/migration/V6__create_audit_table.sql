-- V6__create_audit_table.sql
-- Create audit_events table for audit logging

SET search_path TO incident;

CREATE TABLE IF NOT EXISTS audit_events (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(50),
    service VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    payload_hash VARCHAR(64),
    evidence_id UUID,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_occurred ON audit_events(tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_events(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_events(actor_id, occurred_at DESC);
