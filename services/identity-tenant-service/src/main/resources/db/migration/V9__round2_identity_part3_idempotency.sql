-- V9__round2_identity_part3_idempotency.sql
-- Idempotency keys for user invite creation

CREATE TABLE IF NOT EXISTS identity.idempotency_keys (
    idempotency_key VARCHAR(120) NOT NULL,
    tenant_id UUID NOT NULL,
    scope VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, scope, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at
    ON identity.idempotency_keys (created_at);
