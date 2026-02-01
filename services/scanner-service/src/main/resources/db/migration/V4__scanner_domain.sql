-- V4__scanner_domain.sql
-- Scanner service domain tables

-- Create scanner schema
CREATE SCHEMA IF NOT EXISTS scanner;

-- 1) Scan Sources (what to scan)
CREATE TABLE scanner.scan_sources (
    source_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_name TEXT NOT NULL,
    system_id UUID NOT NULL,
    source_type TEXT NOT NULL,
    status TEXT NOT NULL,
    base_url TEXT NULL,
    auth_type TEXT NOT NULL,
    auth_ref TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT sources_unique_name UNIQUE (tenant_id, source_name)
);

CREATE INDEX idx_sources_tenant_status ON scanner.scan_sources(tenant_id, status);
CREATE INDEX idx_sources_tenant_type ON scanner.scan_sources(tenant_id, source_type);

-- 2) Scan Runs
CREATE TABLE scanner.scan_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_id UUID NOT NULL REFERENCES scanner.scan_sources(source_id),
    scan_mode TEXT NOT NULL,
    status TEXT NOT NULL,
    since_at TIMESTAMPTZ NULL,
    request_ref TEXT NULL,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    findings_count INT NOT NULL DEFAULT 0,
    result_hash TEXT NULL,
    error_message TEXT NULL
);

CREATE INDEX idx_runs_tenant_status_queued ON scanner.scan_runs(tenant_id, status, queued_at);
CREATE INDEX idx_runs_tenant_source_queued ON scanner.scan_runs(tenant_id, source_id, queued_at);

-- 3) Scan Findings
CREATE TABLE scanner.scan_findings (
    finding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES scanner.scan_runs(run_id),
    finding_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    subject_id UUID NULL,
    field_name TEXT NULL,
    data_category TEXT NULL,
    risk_level TEXT NOT NULL,
    confidence INT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_findings_tenant_run ON scanner.scan_findings(tenant_id, run_id);
CREATE INDEX idx_findings_tenant_entity ON scanner.scan_findings(tenant_id, entity_type);
CREATE INDEX idx_findings_tenant_subject ON scanner.scan_findings(tenant_id, subject_id);

-- 4) Scan Promotions
CREATE TABLE scanner.scan_promotions (
    promotion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    promoted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    promoted_count INT NOT NULL,
    failed_count INT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB
);

CREATE INDEX idx_promotions_tenant_run_time ON scanner.scan_promotions(tenant_id, run_id, promoted_at);

-- 5) Scanner Exports
CREATE TABLE scanner.scanner_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scanner_exports_tenant_time ON scanner.scanner_exports(tenant_id, created_at);
