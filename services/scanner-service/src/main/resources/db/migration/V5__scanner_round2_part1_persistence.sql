-- Round 2 Part 1 (persistence only)

-- A) scanned_pages
CREATE TABLE scanner.scanned_pages (
    page_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_id UUID NOT NULL,
    run_id UUID NOT NULL,
    url TEXT NOT NULL,
    url_hash CHAR(64) NOT NULL,
    depth INT NOT NULL DEFAULT 0,
    fetched_at TIMESTAMPTZ NULL,
    http_status INT NULL,
    content_type TEXT NULL,
    title TEXT NULL,
    duration_ms BIGINT NULL,
    page_summary_hash CHAR(64) NULL,
    cookie_count INT NOT NULL DEFAULT 0,
    form_count INT NOT NULL DEFAULT 0,
    tracker_count INT NOT NULL DEFAULT 0,
    has_pii_form_fields BOOLEAN NOT NULL DEFAULT FALSE,
    data_collection_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    sample_text TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_scanned_pages_tenant_run_url_hash
    ON scanner.scanned_pages (tenant_id, run_id, url_hash);
CREATE INDEX idx_scanned_pages_tenant_run
    ON scanner.scanned_pages (tenant_id, run_id);
CREATE INDEX idx_scanned_pages_tenant_source_run
    ON scanner.scanned_pages (tenant_id, source_id, run_id);

-- B) scan_findings fingerprinting columns
ALTER TABLE scanner.scan_findings
    ADD COLUMN finding_fingerprint CHAR(64) NULL,
    ADD COLUMN finding_fingerprint_version SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN normalized_subject TEXT NULL,
    ADD COLUMN key_attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE UNIQUE INDEX uq_scan_findings_tenant_run_fingerprint
    ON scanner.scan_findings (tenant_id, run_id, finding_fingerprint);
CREATE INDEX idx_scan_findings_tenant_fingerprint
    ON scanner.scan_findings (tenant_id, finding_fingerprint);

-- C) remediation_tasks
CREATE TABLE scanner.remediation_tasks (
    task_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_id UUID NOT NULL,
    run_id UUID NOT NULL,
    finding_pk UUID NULL,
    finding_fingerprint CHAR(64) NOT NULL,
    title TEXT NOT NULL,
    severity TEXT NOT NULL,
    owner_user_id UUID NULL,
    owner_email TEXT NULL,
    due_date DATE NULL,
    status TEXT NOT NULL,
    closure_notes TEXT NULL,
    closure_notes_hash CHAR(64) NULL,
    waived_reason TEXT NULL,
    closed_at TIMESTAMPTZ NULL,
    closed_by_user_id UUID NULL,
    evidence_artifact_ref TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_remediation_tasks_severity CHECK (severity IN ('LOW','MED','HIGH','CRITICAL')),
    CONSTRAINT chk_remediation_tasks_status CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED','WAIVED'))
);

CREATE UNIQUE INDEX uq_remediation_tasks_active_fingerprint
    ON scanner.remediation_tasks (tenant_id, finding_fingerprint)
    WHERE status IN ('OPEN','IN_PROGRESS');

CREATE INDEX idx_remediation_tasks_tenant_status_due
    ON scanner.remediation_tasks (tenant_id, status, due_date);
CREATE INDEX idx_remediation_tasks_tenant_source
    ON scanner.remediation_tasks (tenant_id, source_id);
CREATE INDEX idx_remediation_tasks_tenant_run
    ON scanner.remediation_tasks (tenant_id, run_id);
CREATE INDEX idx_remediation_tasks_tenant_finding
    ON scanner.remediation_tasks (tenant_id, finding_pk);

-- D) remediation_task_events
CREATE TABLE scanner.remediation_task_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    from_status TEXT NULL,
    to_status TEXT NULL,
    actor_user_id UUID NULL,
    actor_email TEXT NULL,
    notes TEXT NULL,
    attachment_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remediation_task_events_tenant_task_time
    ON scanner.remediation_task_events (tenant_id, task_id, created_at);
CREATE INDEX idx_remediation_task_events_tenant_time
    ON scanner.remediation_task_events (tenant_id, created_at);

-- E) scan_run_evidence_refs
CREATE TABLE scanner.scan_run_evidence_refs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    bundle_artifact_ref TEXT NOT NULL,
    bundle_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_scan_run_evidence_refs_tenant_run
    ON scanner.scan_run_evidence_refs (tenant_id, run_id);