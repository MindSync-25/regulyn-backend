-- V5: Round 2 - Add connector_credentials and connector_runs tables
SET search_path TO connector;

-- A) connector_credentials: Credential storage with multiple provider support
CREATE TABLE connector_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL CHECK (provider IN ('ENV', 'LOCAL_DB_ENCRYPTED', 'AWS_SECRETS_MANAGER')),
    secret_id VARCHAR(256) NULL,
    secret_version VARCHAR(64) NULL,
    enc_payload BYTEA NULL,
    enc_iv BYTEA NULL,
    enc_kid VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_connector_credentials_tenant_connector UNIQUE (tenant_id, connector_id)
);

CREATE INDEX idx_connector_credentials_tenant ON connector_credentials(tenant_id);
CREATE INDEX idx_connector_credentials_connector ON connector_credentials(connector_id);

COMMENT ON TABLE connector_credentials IS 'Credential storage supporting ENV, encrypted DB, and AWS Secrets Manager';
COMMENT ON COLUMN connector_credentials.provider IS 'Credential provider: ENV, LOCAL_DB_ENCRYPTED, or AWS_SECRETS_MANAGER';
COMMENT ON COLUMN connector_credentials.secret_id IS 'External secret identifier (e.g., AWS secret ARN or env var name)';
COMMENT ON COLUMN connector_credentials.enc_payload IS 'Encrypted credential payload for LOCAL_DB_ENCRYPTED provider';
COMMENT ON COLUMN connector_credentials.enc_iv IS 'Initialization vector for encryption';
COMMENT ON COLUMN connector_credentials.enc_kid IS 'Key ID used for encryption';

-- B) connector_runs: Run tracking with retry logic
CREATE TABLE connector_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    target_id UUID NULL REFERENCES connector_targets(target_id) ON DELETE SET NULL,
    job_type VARCHAR(32) NOT NULL CHECK (job_type IN ('AUDIT_PULL', 'EXPORT', 'DELETE')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL')),
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ NULL,
    correlation_id VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connector_runs_tenant_status_retry ON connector_runs(tenant_id, status, next_retry_at);
CREATE INDEX idx_connector_runs_tenant_connector_created ON connector_runs(tenant_id, connector_id, created_at);
CREATE INDEX idx_connector_runs_correlation ON connector_runs(correlation_id);

COMMENT ON TABLE connector_runs IS 'Run tracking with retry logic for connector jobs';
COMMENT ON COLUMN connector_runs.job_type IS 'Type of job: AUDIT_PULL, EXPORT, or DELETE';
COMMENT ON COLUMN connector_runs.status IS 'Run status: PENDING, RUNNING, SUCCEEDED, FAILED_RETRYABLE, FAILED_TERMINAL';
COMMENT ON COLUMN connector_runs.attempts IS 'Number of execution attempts';
COMMENT ON COLUMN connector_runs.next_retry_at IS 'Timestamp for next retry attempt (NULL if not retryable)';
