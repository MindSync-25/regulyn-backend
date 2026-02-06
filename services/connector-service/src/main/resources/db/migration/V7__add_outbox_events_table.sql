-- V7__add_outbox_events_table.sql
-- Outbox pattern for reliable event publishing

CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    source_service VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(64),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMP,
    correlation_id UUID
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_occurred ON outbox_events(tenant_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_entity ON outbox_events(entity_type, entity_id);
