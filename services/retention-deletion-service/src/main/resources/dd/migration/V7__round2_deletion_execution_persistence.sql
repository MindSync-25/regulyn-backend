/*
INVENTORY SUMMARY (Step 0)
- deletion_requests columns (from V3/V5):
  deletion_id UUID (PK), tenant_id UUID NOT NULL, subject_id UUID NOT NULL, subject_type TEXT NOT NULL,
  entity_type TEXT NOT NULL, source TEXT NOT NULL, reason TEXT NULL, status TEXT NOT NULL,
  due_at TIMESTAMPTZ NOT NULL, assigned_to UUID NULL, requires_approval BOOLEAN, approved_by UUID NULL,
  approved_at TIMESTAMPTZ NULL, proof_required BOOLEAN, closed_at TIMESTAMPTZ NULL,
  idempotency_key TEXT NULL, evidence_bundle_id UUID NULL, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ,
  metadata JSONB
- audit_events (V1): event_id UUID PK, tenant_id UUID NOT NULL, occurred_at TIMESTAMPTZ, actor_id UUID,
  actor_type TEXT, service TEXT, action TEXT, entity_type TEXT, entity_id TEXT, payload_hash TEXT,
  evidence_id UUID, metadata JSONB
- outbox_events (V2): outbox_id UUID PK, tenant_id UUID NOT NULL, event_id UUID UNIQUE, event_type TEXT,
  source_service TEXT, entity_type TEXT, entity_id TEXT, occurred_at TIMESTAMPTZ, payload JSONB,
  payload_hash TEXT, correlation_id TEXT, status TEXT, attempts INT, next_attempt_at TIMESTAMPTZ,
  last_error TEXT, created_at TIMESTAMPTZ
- Entities use @Table(schema = "deletion") (e.g., DeletionRequest, RetentionRule). No base entity,
  no @Version optimistic locking, and no Spring auditing annotations found.
*/

SET search_path TO deletion;

-- 1) deletion_execution_plan
CREATE TABLE deletion_execution_plan (
    plan_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    idempotency_key TEXT NOT NULL,
    plan_version INT NOT NULL DEFAULT 1,
    plan_status TEXT NOT NULL CHECK (plan_status IN ('CREATED','ACTIVE','SUPERSEDED')),
    plan_hash_sha256 CHAR(64) NOT NULL,
    plan_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT fk_deletion_execution_plan_deletion
        FOREIGN KEY (deletion_id) REFERENCES deletion_requests(deletion_id) ON DELETE RESTRICT,
    CONSTRAINT uq_deletion_execution_plan_version
        UNIQUE (tenant_id, deletion_id, plan_version),
    CONSTRAINT uq_deletion_execution_plan_idempotency
        UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_deletion_execution_plan_tenant_deletion
    ON deletion_execution_plan(tenant_id, deletion_id);

CREATE INDEX idx_deletion_execution_plan_tenant_status
    ON deletion_execution_plan(tenant_id, plan_status);

-- 2) deletion_system_execution
CREATE TABLE deletion_system_execution (
    execution_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    system_key TEXT NOT NULL,
    subject_ref TEXT NOT NULL,
    execution_status TEXT NOT NULL CHECK (execution_status IN (
        'PENDING','RUNNING','SUCCEEDED',
        'FAILED_RETRYABLE','FAILED_TERMINAL',
        'EXCEPTION_GRANTED','MANUAL_REQUIRED'
    )),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 8,
    next_retry_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    external_job_ref TEXT NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    last_error_at TIMESTAMPTZ NULL,
    proof_artifact_id UUID NULL,
    exception_artifact_id UUID NULL,
    manual_proof_task_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT fk_deletion_system_execution_deletion
        FOREIGN KEY (deletion_id) REFERENCES deletion_requests(deletion_id) ON DELETE RESTRICT,
    CONSTRAINT fk_deletion_system_execution_plan
        FOREIGN KEY (plan_id) REFERENCES deletion_execution_plan(plan_id) ON DELETE RESTRICT,
    CONSTRAINT uq_deletion_system_execution_identity
        UNIQUE (tenant_id, plan_id, system_key, subject_ref)
);

CREATE INDEX idx_deletion_system_execution_tenant_deletion
    ON deletion_system_execution(tenant_id, deletion_id);

CREATE INDEX idx_deletion_system_execution_tenant_status
    ON deletion_system_execution(tenant_id, execution_status);

CREATE INDEX idx_deletion_system_execution_tenant_status_retry
    ON deletion_system_execution(tenant_id, execution_status, next_retry_at);

-- 3) deletion_manual_proof_task
CREATE TABLE deletion_manual_proof_task (
    task_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    task_status TEXT NOT NULL CHECK (task_status IN ('OPEN','SUBMITTED','APPROVED','REJECTED')),
    requested_reason TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    requested_by UUID NULL,
    submission_artifact_id UUID NULL,
    submission_hash_sha256 CHAR(64) NULL,
    submitted_at TIMESTAMPTZ NULL,
    submitted_by UUID NULL,
    reviewer_comment TEXT NULL,
    decided_at TIMESTAMPTZ NULL,
    decided_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT fk_manual_proof_task_deletion
        FOREIGN KEY (deletion_id) REFERENCES deletion_requests(deletion_id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_proof_task_plan
        FOREIGN KEY (plan_id) REFERENCES deletion_execution_plan(plan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_proof_task_execution
        FOREIGN KEY (execution_id) REFERENCES deletion_system_execution(execution_id) ON DELETE RESTRICT,
    CONSTRAINT uq_manual_proof_task_execution
        UNIQUE (tenant_id, execution_id)
);

CREATE INDEX idx_manual_proof_task_tenant_status
    ON deletion_manual_proof_task(tenant_id, task_status);

CREATE INDEX idx_manual_proof_task_tenant_deletion
    ON deletion_manual_proof_task(tenant_id, deletion_id);

-- Now add FK from deletion_system_execution.manual_proof_task_id -> deletion_manual_proof_task.task_id
ALTER TABLE deletion_system_execution
    ADD CONSTRAINT fk_deletion_system_execution_manual_task
        FOREIGN KEY (manual_proof_task_id) REFERENCES deletion_manual_proof_task(task_id) ON DELETE RESTRICT;

-- 4) deletion_backup_exception
CREATE TABLE deletion_backup_exception (
    exception_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deletion_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    execution_id UUID NULL,
    exception_type TEXT NOT NULL CHECK (exception_type IN ('BACKUP_RETENTION','LEGAL_HOLD','ARCHIVE_RETENTION','OTHER')),
    reason TEXT NOT NULL,
    not_before TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    exception_artifact_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NULL,
    granted_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT fk_backup_exception_deletion
        FOREIGN KEY (deletion_id) REFERENCES deletion_requests(deletion_id) ON DELETE RESTRICT,
    CONSTRAINT fk_backup_exception_plan
        FOREIGN KEY (plan_id) REFERENCES deletion_execution_plan(plan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_backup_exception_execution
        FOREIGN KEY (execution_id) REFERENCES deletion_system_execution(execution_id) ON DELETE RESTRICT
);

CREATE INDEX idx_backup_exception_tenant_deletion
    ON deletion_backup_exception(tenant_id, deletion_id);

CREATE INDEX idx_backup_exception_tenant_status_not_before
    ON deletion_backup_exception(tenant_id, status, not_before);

-- 5) deletion_tombstone
CREATE TABLE deletion_tombstone (
    tombstone_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type TEXT NOT NULL,
    subject_ref TEXT NOT NULL,
    tombstone_status TEXT NOT NULL CHECK (tombstone_status IN ('ACTIVE','REMOVED')),
    reason TEXT NOT NULL,
    created_from_deletion_id UUID NULL,
    created_artifact_id UUID NOT NULL,
    removed_artifact_id UUID NULL,
    removed_at TIMESTAMPTZ NULL,
    removed_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT uq_deletion_tombstone_subject
        UNIQUE (tenant_id, subject_type, subject_ref),
    CONSTRAINT fk_deletion_tombstone_deletion
        FOREIGN KEY (created_from_deletion_id) REFERENCES deletion_requests(deletion_id) ON DELETE RESTRICT
);

CREATE INDEX idx_deletion_tombstone_tenant_status
    ON deletion_tombstone(tenant_id, tombstone_status);

CREATE INDEX idx_deletion_tombstone_tenant_subject
    ON deletion_tombstone(tenant_id, subject_type, subject_ref);
