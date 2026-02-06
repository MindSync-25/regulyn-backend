-- V6: Round 2 - Add connector_schedules, webhook_events, and connector_cursor_state tables
SET search_path TO connector;

-- A) connector_schedules: Schedule configuration for periodic jobs
CREATE TABLE connector_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    target_id UUID NULL REFERENCES connector_targets(target_id) ON DELETE CASCADE,
    job_type VARCHAR(32) NOT NULL CHECK (job_type IN ('AUDIT_PULL', 'EXPORT')),
    cron VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    enabled BOOLEAN NOT NULL DEFAULT true,
    next_fire_at TIMESTAMPTZ NOT NULL,
    last_fire_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connector_schedules_tenant_enabled_fire ON connector_schedules(tenant_id, enabled, next_fire_at);
CREATE INDEX idx_connector_schedules_tenant_connector ON connector_schedules(tenant_id, connector_id);

COMMENT ON TABLE connector_schedules IS 'Scheduled job configuration for connectors';
COMMENT ON COLUMN connector_schedules.job_type IS 'Job type: AUDIT_PULL or EXPORT';
COMMENT ON COLUMN connector_schedules.cron IS 'Cron expression for scheduling';
COMMENT ON COLUMN connector_schedules.timezone IS 'Timezone for cron evaluation';
COMMENT ON COLUMN connector_schedules.next_fire_at IS 'Next scheduled execution time';

-- B) webhook_events: Incoming webhook event storage
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    signature_valid BOOLEAN NOT NULL,
    headers_json JSONB NULL,
    payload_hash VARCHAR(64) NOT NULL,
    raw_payload_json JSONB NOT NULL,
    normalized_type VARCHAR(128) NULL,
    normalized_subject VARCHAR(256) NULL,
    correlation_id VARCHAR(64) NOT NULL
);

CREATE INDEX idx_webhook_events_tenant_connector_received ON webhook_events(tenant_id, connector_id, received_at);
CREATE INDEX idx_webhook_events_tenant_correlation ON webhook_events(tenant_id, correlation_id);

COMMENT ON TABLE webhook_events IS 'Incoming webhook events with signature validation';
COMMENT ON COLUMN webhook_events.payload_hash IS 'SHA-256 hash of raw payload (64-char hex)';
COMMENT ON COLUMN webhook_events.signature_valid IS 'Whether webhook signature was validated successfully';
COMMENT ON COLUMN webhook_events.normalized_type IS 'Normalized event type after provider-specific parsing';

-- C) connector_cursor_state: Persistent cursor state for incremental sync
CREATE TABLE connector_cursor_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    target_id UUID NULL REFERENCES connector_targets(target_id) ON DELETE CASCADE,
    job_type VARCHAR(32) NOT NULL CHECK (job_type IN ('AUDIT_PULL', 'EXPORT')),
    cursor_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_connector_cursor_state_composite UNIQUE NULLS NOT DISTINCT (tenant_id, connector_id, target_id, job_type)
);

CREATE INDEX idx_connector_cursor_state_tenant_connector ON connector_cursor_state(tenant_id, connector_id);

COMMENT ON TABLE connector_cursor_state IS 'Persistent cursor state for incremental synchronization';
COMMENT ON COLUMN connector_cursor_state.cursor_json IS 'Provider-specific cursor state (e.g., last timestamp, page token)';
COMMENT ON COLUMN connector_cursor_state.job_type IS 'Job type: AUDIT_PULL or EXPORT';
