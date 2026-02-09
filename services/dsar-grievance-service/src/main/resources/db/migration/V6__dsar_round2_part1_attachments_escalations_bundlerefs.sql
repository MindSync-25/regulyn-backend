-- V6__dsar_round2_part1_attachments_escalations_bundlerefs.sql
SET search_path TO dsar;

-- TABLE 1: dsar_attachments
CREATE TABLE dsar.dsar_attachments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  dsar_id UUID NOT NULL,

  attachment_type VARCHAR(16) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  idempotency_key VARCHAR(128) NULL,

  filename TEXT NULL,
  content_type TEXT NULL,
  size_bytes BIGINT NULL,

  sha256 CHAR(64) NULL,
  artifact_ref TEXT NULL,

  reference_value TEXT NULL,
  reference_hash CHAR(64) NULL,
  recorded_evidence_artifact_ref TEXT NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

ALTER TABLE dsar.dsar_attachments
  ADD CONSTRAINT fk_dsar_attachments_dsar
  FOREIGN KEY (dsar_id) REFERENCES dsar.dsar_requests(request_id_pk);

ALTER TABLE dsar.dsar_attachments
  ADD CONSTRAINT ck_dsar_attachments_type
  CHECK (attachment_type IN ('UPLOAD','REFERENCE'));

ALTER TABLE dsar.dsar_attachments
  ADD CONSTRAINT ck_dsar_attachments_reference_value
  CHECK (attachment_type <> 'REFERENCE' OR reference_value IS NOT NULL);

CREATE UNIQUE INDEX ux_dsar_attachments_tenant_dsar_version
  ON dsar.dsar_attachments (tenant_id, dsar_id, version);

CREATE UNIQUE INDEX ux_dsar_attachments_tenant_dsar_idempotency
  ON dsar.dsar_attachments (tenant_id, dsar_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_dsar_attachments_tenant_dsar_created
  ON dsar.dsar_attachments (tenant_id, dsar_id, created_at);

-- TABLE 2: dsar_escalation_tasks
CREATE TABLE dsar.dsar_escalation_tasks (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  dsar_id UUID NOT NULL,

  threshold VARCHAR(16) NOT NULL,
  threshold_days INT NOT NULL,
  due_at TIMESTAMPTZ NOT NULL,
  reached_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  status VARCHAR(24) NOT NULL,
  notification_request_id TEXT NULL,
  provider_message_id TEXT NULL,
  last_error TEXT NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE dsar.dsar_escalation_tasks
  ADD CONSTRAINT fk_dsar_escalation_tasks_dsar
  FOREIGN KEY (dsar_id) REFERENCES dsar.dsar_requests(request_id_pk);

ALTER TABLE dsar.dsar_escalation_tasks
  ADD CONSTRAINT ck_dsar_escalation_threshold
  CHECK (threshold IN ('D60','D80','D90'));

ALTER TABLE dsar.dsar_escalation_tasks
  ADD CONSTRAINT ck_dsar_escalation_status
  CHECK (status IN ('CREATED','NOTIFICATION_QUEUED','NOTIFICATION_SENT','SUPPRESSED_CLOSED'));

ALTER TABLE dsar.dsar_escalation_tasks
  ADD CONSTRAINT ck_dsar_escalation_threshold_days
  CHECK (
    (threshold='D60' AND threshold_days=60) OR
    (threshold='D80' AND threshold_days=80) OR
    (threshold='D90' AND threshold_days=90)
  );

CREATE UNIQUE INDEX ux_dsar_escalation_tasks_tenant_dsar_threshold
  ON dsar.dsar_escalation_tasks (tenant_id, dsar_id, threshold);

CREATE INDEX ix_dsar_escalation_tasks_tenant_status_due
  ON dsar.dsar_escalation_tasks (tenant_id, status, due_at);

CREATE INDEX ix_dsar_escalation_tasks_tenant_dsar
  ON dsar.dsar_escalation_tasks (tenant_id, dsar_id);

-- TABLE 3: dsar_evidence_bundle_refs
CREATE TABLE dsar.dsar_evidence_bundle_refs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  dsar_id UUID NOT NULL,
  close_event_id UUID NOT NULL,

  bundle_ref TEXT NULL,
  bundle_sha256 CHAR(64) NULL,

  status VARCHAR(24) NOT NULL,
  last_error TEXT NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE dsar.dsar_evidence_bundle_refs
  ADD CONSTRAINT fk_dsar_bundle_refs_dsar
  FOREIGN KEY (dsar_id) REFERENCES dsar.dsar_requests(request_id_pk);

ALTER TABLE dsar.dsar_evidence_bundle_refs
  ADD CONSTRAINT ck_dsar_bundle_refs_status
  CHECK (status IN ('CREATED','BUNDLE_QUEUED','BUNDLE_STORED','FAILED'));

CREATE UNIQUE INDEX ux_dsar_bundle_refs_tenant_dsar_close_event
  ON dsar.dsar_evidence_bundle_refs (tenant_id, dsar_id, close_event_id);

CREATE INDEX ix_dsar_bundle_refs_tenant_dsar_created
  ON dsar.dsar_evidence_bundle_refs (tenant_id, dsar_id, created_at);