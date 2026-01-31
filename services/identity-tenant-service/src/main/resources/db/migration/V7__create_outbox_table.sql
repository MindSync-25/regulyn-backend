-- V7__create_outbox_table.sql
CREATE TABLE outbox_events (
    outbox_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    source_service TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    payload_hash TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_tenant_occurred ON outbox_events(tenant_id, occurred_at);
CREATE INDEX idx_outbox_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX idx_outbox_entity ON outbox_events(entity_type, entity_id);

COMMENT ON TABLE outbox_events IS 'Outbox pattern for reliable event publishing';
COMMENT ON COLUMN outbox_events.status IS 'PENDING, PUBLISHED, or FAILED';
