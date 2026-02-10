-- Round 2 Hardening Part 1 (Employee Data Service)
-- New persistence objects for resumes, document rules/metadata, exit workflows, and access logs.

CREATE TABLE employee.employee_resumes (
    resume_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NULL REFERENCES employee.employees(employee_id) ON DELETE SET NULL,
    employee_ref TEXT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    source TEXT NOT NULL,
    retention_days INT NOT NULL,
    delete_after TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    storage_ref TEXT NOT NULL,
    checksum_sha256 TEXT NULL,
    deleted_at TIMESTAMPTZ NULL,
    deletion_artifact_ref TEXT NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    idempotency_key TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_resumes_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_employee_resumes_status CHECK (status IN ('ACTIVE','DELETION_SCHEDULED','DELETED','FAILED_RETRYABLE')),
    CONSTRAINT ck_employee_resumes_employee_ref CHECK (employee_id IS NOT NULL OR employee_ref IS NOT NULL)
);

CREATE UNIQUE INDEX uq_employee_resumes_tenant_idem ON employee.employee_resumes(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_employee_resumes_tenant_delete_after ON employee.employee_resumes(tenant_id, delete_after);
CREATE INDEX idx_employee_resumes_tenant_status ON employee.employee_resumes(tenant_id, status);

CREATE TABLE employee.hr_document_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    doc_type TEXT NOT NULL,
    sensitivity TEXT NOT NULL,
    allowed_roles TEXT[] NOT NULL,
    retention_days INT NOT NULL,
    encryption_required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_document_rules_doc_type CHECK (doc_type IN ('OFFER_LETTER','ID_PROOF','BANK_DETAILS','PERFORMANCE_REVIEW','MEDICAL','EXIT_FORM','CONTRACT','OTHER')),
    CONSTRAINT ck_hr_document_rules_sensitivity CHECK (sensitivity IN ('LOW','MED','HIGH')),
    CONSTRAINT ck_hr_document_rules_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_hr_document_rules_allowed_roles CHECK (array_length(allowed_roles, 1) > 0),
    CONSTRAINT uq_hr_document_rules_tenant_doc_type UNIQUE (tenant_id, doc_type)
);

CREATE INDEX idx_hr_document_rules_tenant_active ON employee.hr_document_rules(tenant_id, active);

CREATE TABLE employee.hr_document_metadata (
    doc_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL REFERENCES employee.employees(employee_id) ON DELETE CASCADE,
    doc_type TEXT NOT NULL,
    sensitivity TEXT NOT NULL,
    storage_ref TEXT NOT NULL,
    checksum_sha256 TEXT NULL,
    retention_days INT NOT NULL,
    delete_after TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    uploaded_by UUID NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    idempotency_key TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_document_metadata_doc_type CHECK (doc_type IN ('OFFER_LETTER','ID_PROOF','BANK_DETAILS','PERFORMANCE_REVIEW','MEDICAL','EXIT_FORM','CONTRACT','OTHER')),
    CONSTRAINT ck_hr_document_metadata_sensitivity CHECK (sensitivity IN ('LOW','MED','HIGH')),
    CONSTRAINT ck_hr_document_metadata_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_hr_document_metadata_status CHECK (status IN ('ACTIVE','DELETION_SCHEDULED','DELETED'))
);

CREATE UNIQUE INDEX uq_hr_document_metadata_tenant_employee_idem ON employee.hr_document_metadata(tenant_id, employee_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_hr_document_metadata_tenant_employee ON employee.hr_document_metadata(tenant_id, employee_id);
CREATE INDEX idx_hr_document_metadata_tenant_doc_type ON employee.hr_document_metadata(tenant_id, doc_type);
CREATE INDEX idx_hr_document_metadata_tenant_delete_after ON employee.hr_document_metadata(tenant_id, delete_after);

CREATE TABLE employee.employee_exit_workflows (
    workflow_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL REFERENCES employee.employees(employee_id) ON DELETE CASCADE,
    terminated_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    subject_ref TEXT NULL,
    retention_deletion_request_ref TEXT NULL,
    disable_access_task_ref TEXT NULL,
    evidence_bundle_ref TEXT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_exit_workflows_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','FAILED_RETRYABLE','FAILED_TERMINAL')),
    CONSTRAINT uq_employee_exit_workflows_employee_terminated UNIQUE (tenant_id, employee_id, terminated_at)
);

CREATE INDEX idx_employee_exit_workflows_tenant_status ON employee.employee_exit_workflows(tenant_id, status);

CREATE TABLE employee.employee_exit_workflow_steps (
    step_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL REFERENCES employee.employee_exit_workflows(workflow_id) ON DELETE CASCADE,
    step_key TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_exit_workflow_steps_key CHECK (step_key IN ('DISABLE_ACCESS','RETENTION_DELETION_REQUEST','EVIDENCE_BUNDLE_CREATE')),
    CONSTRAINT ck_employee_exit_workflow_steps_status CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','FAILED_RETRYABLE','FAILED_TERMINAL')),
    CONSTRAINT ck_employee_exit_workflow_steps_attempt CHECK (attempt_count >= 0),
    CONSTRAINT uq_employee_exit_workflow_steps_key UNIQUE (tenant_id, workflow_id, step_key)
);

CREATE INDEX idx_employee_exit_workflow_steps_tenant_workflow ON employee.employee_exit_workflow_steps(tenant_id, workflow_id);

CREATE TABLE employee.employee_access_logs (
    access_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    employee_id UUID NULL REFERENCES employee.employees(employee_id) ON DELETE SET NULL,
    doc_id UUID NULL REFERENCES employee.hr_document_metadata(doc_id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    result TEXT NOT NULL,
    reason TEXT NULL,
    ip INET NULL,
    user_agent TEXT NULL,
    request_id TEXT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_access_logs_action CHECK (action IN ('VIEW','DOWNLOAD','UPDATE')),
    CONSTRAINT ck_employee_access_logs_result CHECK (result IN ('ALLOWED','DENIED')),
    CONSTRAINT ck_employee_access_logs_subject CHECK (employee_id IS NOT NULL OR doc_id IS NOT NULL)
);

CREATE INDEX idx_employee_access_logs_tenant_employee ON employee.employee_access_logs(tenant_id, employee_id, occurred_at);
CREATE INDEX idx_employee_access_logs_tenant_user ON employee.employee_access_logs(tenant_id, user_id, occurred_at);
CREATE INDEX idx_employee_access_logs_tenant_doc ON employee.employee_access_logs(tenant_id, doc_id, occurred_at);

CREATE TABLE employee.employee_access_log_exports (
    access_export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    export_scope TEXT NOT NULL,
    employee_id UUID NULL,
    user_id UUID NULL,
    from_ts TIMESTAMPTZ NULL,
    to_ts TIMESTAMPTZ NULL,
    status TEXT NOT NULL,
    artifact_ref TEXT NULL,
    artifact_hash TEXT NULL,
    idempotency_key TEXT NOT NULL,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_access_log_exports_scope CHECK (export_scope IN ('BY_EMPLOYEE','BY_USER','BY_DATE_RANGE')),
    CONSTRAINT ck_employee_access_log_exports_status CHECK (status IN ('CREATED','ARTIFACT_STORED','FAILED_RETRYABLE','FAILED_TERMINAL')),
    CONSTRAINT ck_employee_access_log_exports_filters CHECK (
        (export_scope = 'BY_EMPLOYEE' AND employee_id IS NOT NULL AND user_id IS NULL AND from_ts IS NULL AND to_ts IS NULL)
        OR (export_scope = 'BY_USER' AND user_id IS NOT NULL AND employee_id IS NULL AND from_ts IS NULL AND to_ts IS NULL)
        OR (export_scope = 'BY_DATE_RANGE' AND from_ts IS NOT NULL AND to_ts IS NOT NULL AND from_ts <= to_ts)
    ),
    CONSTRAINT uq_employee_access_log_exports_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_employee_access_log_exports_tenant_created ON employee.employee_access_log_exports(tenant_id, created_at);

-- Backward-compatible alters to employee_exports
DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN idempotency_key TEXT NULL;
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN export_type TEXT NULL;
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN params JSONB NULL;
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN artifact_ref TEXT NULL;
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN artifact_hash TEXT NULL;
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE employee.employee_exports ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
EXCEPTION WHEN duplicate_column THEN
    NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_exports_idempotency ON employee.employee_exports(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
