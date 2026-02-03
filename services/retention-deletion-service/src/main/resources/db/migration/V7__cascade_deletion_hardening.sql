-- V7__cascade_deletion_hardening.sql
-- ROUND 2: Cascade deletion execution with connector integration, backup exceptions, and proof completeness

SET search_path TO deletion;

-- =====================================================
-- 1) DELETION EXECUTION PLAN
-- Stores the execution plan for cascading deletion across systems
-- =====================================================
CREATE TABLE IF NOT EXISTS deletion_execution_plan (
    plan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_request_id UUID NOT NULL UNIQUE,
    plan_version INT NOT NULL DEFAULT 1,
    plan_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_plan_deletion_request FOREIGN KEY (deletion_request_id) 
        REFERENCES deletion_requests(deletion_id) ON DELETE CASCADE
);

CREATE INDEX idx_plan_tenant ON deletion_execution_plan(tenant_id);
CREATE INDEX idx_plan_deletion_request ON deletion_execution_plan(deletion_request_id);

COMMENT ON TABLE deletion_execution_plan IS 'Execution plan for cascading deletions across integrated systems';
COMMENT ON COLUMN deletion_execution_plan.plan_json IS 'JSON describing systems, entities, targets, and required proofs';
COMMENT ON COLUMN deletion_execution_plan.plan_version IS 'Version for plan evolution tracking';

-- =====================================================
-- 2) DELETION SYSTEM EXECUTION
-- Tracks execution status per system with connector integration
-- =====================================================
CREATE TABLE IF NOT EXISTS deletion_system_execution (
    exec_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_request_id UUID NOT NULL,
    system_name VARCHAR(120) NOT NULL,
    connector_id UUID NULL,
    target_id UUID NULL,
    action_type VARCHAR(32) NOT NULL DEFAULT 'DELETE',
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message TEXT NULL,
    connector_run_id UUID NULL,
    connector_job_id UUID NULL,
    proof_artifact_ref VARCHAR(200) NULL,
    exception_artifact_ref VARCHAR(200) NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_system_exec_deletion_request FOREIGN KEY (deletion_request_id) 
        REFERENCES deletion_requests(deletion_id) ON DELETE CASCADE,
    CONSTRAINT chk_system_exec_status CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 
        'FAILED_TERMINAL', 'MANUAL_REQUIRED', 'EXCEPTION_GRANTED'
    )),
    CONSTRAINT chk_system_exec_action CHECK (action_type IN ('DELETE', 'EXPORT'))
);

CREATE INDEX idx_system_exec_tenant_request ON deletion_system_execution(tenant_id, deletion_request_id);
CREATE INDEX idx_system_exec_retry ON deletion_system_execution(tenant_id, status, next_retry_at) 
    WHERE status = 'FAILED_RETRYABLE' AND next_retry_at IS NOT NULL;
CREATE INDEX idx_system_exec_connector ON deletion_system_execution(connector_run_id) 
    WHERE connector_run_id IS NOT NULL;

COMMENT ON TABLE deletion_system_execution IS 'Per-system execution tracking with connector integration';
COMMENT ON COLUMN deletion_system_execution.status IS 'PENDING|RUNNING|SUCCEEDED|FAILED_RETRYABLE|FAILED_TERMINAL|MANUAL_REQUIRED|EXCEPTION_GRANTED';
COMMENT ON COLUMN deletion_system_execution.connector_run_id IS 'Connector service run ID for idempotency and status polling';
COMMENT ON COLUMN deletion_system_execution.proof_artifact_ref IS 'Evidence service artifact reference for deletion proof';
COMMENT ON COLUMN deletion_system_execution.exception_artifact_ref IS 'Evidence artifact for exception (backup/legal hold)';

-- =====================================================
-- 3) DELETION BACKUP EXCEPTION
-- Handles systems that cannot delete due to immutable backups or legal holds
-- =====================================================
CREATE TABLE IF NOT EXISTS deletion_backup_exception (
    exception_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_request_id UUID NOT NULL,
    system_name VARCHAR(120) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    retention_until TIMESTAMPTZ NULL,
    notes TEXT NULL,
    exception_artifact_ref VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_backup_exception_deletion_request FOREIGN KEY (deletion_request_id) 
        REFERENCES deletion_requests(deletion_id) ON DELETE CASCADE,
    CONSTRAINT uq_backup_exception_system UNIQUE (tenant_id, deletion_request_id, system_name)
);

CREATE INDEX idx_backup_exception_tenant ON deletion_backup_exception(tenant_id);
CREATE INDEX idx_backup_exception_purge ON deletion_backup_exception(retention_until) 
    WHERE retention_until IS NOT NULL;

COMMENT ON TABLE deletion_backup_exception IS 'Explicit exceptions for systems that cannot delete yet';
COMMENT ON COLUMN deletion_backup_exception.reason_code IS 'BACKUP_IMMUTABLE|LEGAL_HOLD|VENDOR_LIMITATION|OTHER';
COMMENT ON COLUMN deletion_backup_exception.retention_until IS 'When backup/hold expires and deletion can be reattempted';
COMMENT ON COLUMN deletion_backup_exception.exception_artifact_ref IS 'Evidence artifact explaining why deletion cannot proceed';

-- =====================================================
-- 4) DELETION TOMBSTONE
-- Prevents accidental recreation of deleted subjects
-- =====================================================
CREATE TABLE IF NOT EXISTS deletion_tombstone (
    tombstone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_ref VARCHAR(200) NOT NULL,
    deletion_request_id UUID NOT NULL,
    tombstoned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_tombstone_deletion_request FOREIGN KEY (deletion_request_id) 
        REFERENCES deletion_requests(deletion_id) ON DELETE CASCADE,
    CONSTRAINT uq_tombstone_subject UNIQUE (tenant_id, subject_ref)
);

CREATE INDEX idx_tombstone_tenant ON deletion_tombstone(tenant_id);
CREATE INDEX idx_tombstone_subject ON deletion_tombstone(subject_ref);

COMMENT ON TABLE deletion_tombstone IS 'Tombstone records to prevent re-creation of deleted subjects';
COMMENT ON COLUMN deletion_tombstone.subject_ref IS 'Subject identifier (email, customer ID, etc.) as used in deletion request';

-- =====================================================
-- 5) DELETION MANUAL PROOF TASK
-- Handles manual proof submission for systems requiring human intervention
-- =====================================================
CREATE TABLE IF NOT EXISTS deletion_manual_proof_task (
    task_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deletion_request_id UUID NOT NULL,
    system_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ NULL,
    approved_at TIMESTAMPTZ NULL,
    proof_artifact_ref VARCHAR(200) NULL,
    reviewer_user_id UUID NULL,
    
    CONSTRAINT fk_manual_task_deletion_request FOREIGN KEY (deletion_request_id) 
        REFERENCES deletion_requests(deletion_id) ON DELETE CASCADE,
    CONSTRAINT uq_manual_task_system UNIQUE (tenant_id, deletion_request_id, system_name),
    CONSTRAINT chk_manual_task_status CHECK (status IN ('OPEN', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_manual_task_tenant ON deletion_manual_proof_task(tenant_id);
CREATE INDEX idx_manual_task_status ON deletion_manual_proof_task(tenant_id, status);

COMMENT ON TABLE deletion_manual_proof_task IS 'Manual proof submission workflow for systems requiring human intervention';
COMMENT ON COLUMN deletion_manual_proof_task.status IS 'OPEN|SUBMITTED|APPROVED|REJECTED';
COMMENT ON COLUMN deletion_manual_proof_task.reviewer_user_id IS 'User who approved/rejected the manual proof';

-- =====================================================
-- GRANT PERMISSIONS (if using specific roles)
-- =====================================================
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA deletion TO retention_app_role;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA deletion TO retention_app_role;
