-- Connector domain tables
SET search_path TO connector;

-- 1) Connectors table
CREATE TABLE connectors (
    connector_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_name TEXT NOT NULL,
    connector_type TEXT NOT NULL,
    status TEXT NOT NULL,
    base_url TEXT NULL,
    auth_type TEXT NOT NULL,
    auth_ref TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT connectors_unique_name UNIQUE (tenant_id, connector_name)
);

CREATE INDEX idx_connectors_tenant_status ON connectors(tenant_id, status);
CREATE INDEX idx_connectors_tenant_type ON connectors(tenant_id, connector_type);

-- 2) Connector targets table
CREATE TABLE connector_targets (
    target_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id) ON DELETE CASCADE,
    target_key TEXT NOT NULL,
    target_type TEXT NOT NULL,
    subject_type TEXT NOT NULL,
    supported_actions TEXT[] NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT targets_unique_key UNIQUE (tenant_id, connector_id, target_key)
);

CREATE INDEX idx_targets_tenant_connector ON connector_targets(tenant_id, connector_id);

-- 3) Connector jobs table
CREATE TABLE connector_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL,
    connector_id UUID NOT NULL REFERENCES connectors(connector_id),
    target_id UUID NOT NULL REFERENCES connector_targets(target_id),
    subject_id UUID NOT NULL,
    subject_type TEXT NOT NULL,
    request_ref TEXT NULL,
    idempotency_key TEXT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    callback_url TEXT NULL,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    result_hash TEXT NULL,
    error_message TEXT NULL,
    CONSTRAINT jobs_unique_idempotency UNIQUE NULLS NOT DISTINCT (tenant_id, subject_id, connector_id, target_id, idempotency_key)
);

CREATE INDEX idx_jobs_tenant_status_queued ON connector_jobs(tenant_id, status, queued_at);
CREATE INDEX idx_jobs_tenant_request_ref ON connector_jobs(tenant_id, request_ref);

-- 4) Connector job logs table (append-only)
CREATE TABLE connector_job_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    job_id UUID NOT NULL REFERENCES connector_jobs(job_id) ON DELETE CASCADE,
    step TEXT NOT NULL,
    status TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_logs_tenant_job_created ON connector_job_logs(tenant_id, job_id, created_at);

-- 5) Connector exports table
CREATE TABLE connector_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connector_exports_tenant_created ON connector_exports(tenant_id, created_at);
