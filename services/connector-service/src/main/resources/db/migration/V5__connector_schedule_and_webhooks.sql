-- Connector scheduler and webhook tables
SET search_path TO connector;

-- 1) Connector schedules table
CREATE TABLE connector_schedules (
    schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES connector_targets(target_id) ON DELETE CASCADE,
    job_type TEXT NOT NULL CHECK (job_type IN ('AUDIT_PULL', 'EXPORT', 'SYNC')),
    cron_expr TEXT NULL,
    interval_seconds INTEGER NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMPTZ NULL,
    next_run_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT schedules_unique_connector_target UNIQUE (tenant_id, connector_id, target_id, job_type),
    CONSTRAINT schedules_cron_or_interval CHECK (
        (cron_expr IS NOT NULL AND interval_seconds IS NULL) OR
        (cron_expr IS NULL AND interval_seconds IS NOT NULL)
    )
);

CREATE INDEX idx_schedules_tenant_enabled ON connector_schedules(tenant_id, enabled);
CREATE INDEX idx_schedules_next_run ON connector_schedules(next_run_at) WHERE enabled = true;
CREATE INDEX idx_schedules_connector ON connector_schedules(connector_id);

-- 2) Webhook events table
CREATE TABLE webhook_events (
    webhook_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id),
    provider TEXT NOT NULL,
    event_type TEXT NOT NULL,
    normalized_event_type TEXT NULL,
    signature TEXT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT false,
    raw_payload JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT false,
    processed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_tenant_created ON webhook_events(tenant_id, created_at);
CREATE INDEX idx_webhook_events_connector_processed ON webhook_events(connector_id, processed);
CREATE INDEX idx_webhook_events_provider_type ON webhook_events(provider, event_type);

-- 3) Connector runs table (unified for scheduled + webhook + manual)
CREATE TABLE connector_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id),
    target_id UUID NULL REFERENCES connector_targets(target_id),
    run_type TEXT NOT NULL CHECK (run_type IN ('SCHEDULED', 'WEBHOOK', 'MANUAL')),
    schedule_id UUID NULL REFERENCES connector_schedules(schedule_id),
    webhook_event_id UUID NULL REFERENCES webhook_events(webhook_event_id),
    job_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    scheduled_fire_time TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    receipt JSONB NULL,
    error_message TEXT NULL,
    idempotency_key TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT runs_unique_scheduled_fire UNIQUE (schedule_id, scheduled_fire_time)
);

CREATE INDEX idx_connector_runs_tenant_status ON connector_runs(tenant_id, status);
CREATE INDEX idx_connector_runs_schedule ON connector_runs(schedule_id);
CREATE INDEX idx_connector_runs_webhook ON connector_runs(webhook_event_id);
CREATE INDEX idx_connector_runs_created ON connector_runs(created_at);
CREATE INDEX idx_connector_runs_idempotency ON connector_runs(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 4) Connector credentials table (encrypted at rest)
CREATE TABLE connector_credentials (
    credential_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    credential_type TEXT NOT NULL CHECK (credential_type IN ('API_KEY', 'OAUTH2', 'BASIC_AUTH', 'BEARER_TOKEN', 'HMAC_SECRET')),
    encrypted_value BYTEA NOT NULL,
    encryption_key_ref TEXT NOT NULL,
    aws_secret_arn TEXT NULL,
    expires_at TIMESTAMPTZ NULL,
    rotation_required BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT credentials_unique_connector_type UNIQUE (connector_id, credential_type)
);

CREATE INDEX idx_credentials_tenant ON connector_credentials(tenant_id);
CREATE INDEX idx_credentials_connector ON connector_credentials(connector_id);
CREATE INDEX idx_credentials_rotation ON connector_credentials(rotation_required) WHERE rotation_required = true;
CREATE INDEX idx_credentials_aws_secret ON connector_credentials(aws_secret_arn) WHERE aws_secret_arn IS NOT NULL;
