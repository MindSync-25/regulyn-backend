-- Round 2 Hardening Part 2 (Resume retention executions)

CREATE TABLE employee.employee_resume_deletion_executions (
    execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    resume_id UUID NOT NULL REFERENCES employee.employee_resumes(resume_id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    attempt_no INT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL,
    artifact_ref TEXT NULL,
    artifact_hash TEXT NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_employee_resume_deletion_executions_status CHECK (status IN ('STARTED','SUCCEEDED','FAILED_RETRYABLE','FAILED_TERMINAL')),
    CONSTRAINT ck_employee_resume_deletion_executions_attempt CHECK (attempt_no >= 1)
);

CREATE UNIQUE INDEX uq_employee_resume_deletion_executions_attempt
    ON employee.employee_resume_deletion_executions(tenant_id, resume_id, attempt_no);

CREATE UNIQUE INDEX uq_employee_resume_deletion_executions_started
    ON employee.employee_resume_deletion_executions(tenant_id, resume_id)
    WHERE status = 'STARTED';

CREATE INDEX idx_employee_resume_deletion_executions_resume
    ON employee.employee_resume_deletion_executions(tenant_id, resume_id);

CREATE INDEX idx_employee_resume_deletion_executions_status
    ON employee.employee_resume_deletion_executions(tenant_id, status, started_at);
