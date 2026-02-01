-- V5__create_outbox_table.sql
-- Create outbox_events table for event publishing

SET search_path TO incident;

CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64),
    correlation_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_created ON outbox_events(tenant_id, created_at DESC);
