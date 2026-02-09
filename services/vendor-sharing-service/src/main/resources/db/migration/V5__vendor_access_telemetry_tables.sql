-- V5: Vendor Access Telemetry Tables

CREATE TABLE vendor.vendor_access_events (
    access_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    system_name TEXT NOT NULL,
    source TEXT NULL,
    access_type TEXT NOT NULL,
    subject_ref TEXT NULL,
    data_categories TEXT[] NULL,
    purpose_ref TEXT NULL,
    purpose_version BIGINT NULL,
    accessed_at TIMESTAMPTZ NOT NULL,
    correlation_id TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id TEXT NULL,
    ip TEXT NULL,
    user_agent TEXT NULL,
    result TEXT NOT NULL,
    raw_payload_hash TEXT NOT NULL,
    raw_payload_ref JSONB NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_access_events_idempotency UNIQUE (tenant_id, vendor_id, correlation_id, accessed_at, access_type)
);

CREATE INDEX idx_va_events_tenant_vendor_time
    ON vendor.vendor_access_events (tenant_id, vendor_id, accessed_at DESC);
CREATE INDEX idx_va_events_tenant_subject_time
    ON vendor.vendor_access_events (tenant_id, subject_ref, accessed_at DESC)
    WHERE subject_ref IS NOT NULL;
CREATE INDEX idx_va_events_tenant_system_time
    ON vendor.vendor_access_events (tenant_id, system_name, accessed_at DESC);
CREATE INDEX idx_va_events_tenant_type_time
    ON vendor.vendor_access_events (tenant_id, access_type, accessed_at DESC);
CREATE INDEX idx_va_events_tenant_result_time
    ON vendor.vendor_access_events (tenant_id, result, accessed_at DESC);
CREATE INDEX idx_va_events_tenant_corr
    ON vendor.vendor_access_events (tenant_id, correlation_id);

CREATE TABLE vendor.vendor_access_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    requested_by_user_id UUID NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    range_start TIMESTAMPTZ NOT NULL,
    range_end TIMESTAMPTZ NOT NULL,
    format TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'REQUESTED',
    idempotency_key TEXT NULL,
    artifact_ref TEXT NULL,
    file_hash TEXT NULL,
    total_events BIGINT NOT NULL DEFAULT 0,
    allowed_events BIGINT NOT NULL DEFAULT 0,
    denied_events BIGINT NOT NULL DEFAULT 0,
    error_events BIGINT NOT NULL DEFAULT 0,
    notes TEXT NULL
);

CREATE INDEX idx_va_exports_tenant_vendor_time
    ON vendor.vendor_access_exports (tenant_id, vendor_id, requested_at DESC);

CREATE UNIQUE INDEX uq_va_exports_tenant_idempotency
    ON vendor.vendor_access_exports (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
