-- V4__enhance_dsar_workflow.sql
-- Enhance DSAR tables for full workflow support

SET search_path TO dsar;

-- Add workflow columns to dsar_requests
ALTER TABLE dsar_requests ADD COLUMN data_principal_id UUID;
ALTER TABLE dsar_requests ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE dsar_requests ADD COLUMN due_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '90 days');
ALTER TABLE dsar_requests ADD COLUMN assigned_to UUID;
ALTER TABLE dsar_requests ADD COLUMN requires_approval BOOLEAN DEFAULT false;
ALTER TABLE dsar_requests ADD COLUMN approved_by UUID;
ALTER TABLE dsar_requests ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE dsar_requests ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE dsar_requests ADD COLUMN close_evidence_bundle_id UUID;
ALTER TABLE dsar_requests ADD COLUMN metadata JSONB DEFAULT '{}';

-- Create status history table
CREATE TABLE dsar_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    dsar_id UUID NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ DEFAULT NOW(),
    changed_by UUID,
    reason TEXT,
    CONSTRAINT fk_dsar_history_request FOREIGN KEY (dsar_id) REFERENCES dsar_requests(request_id_pk)
);

CREATE INDEX idx_dsar_history_dsar ON dsar_status_history(dsar_id, changed_at DESC);
CREATE INDEX idx_dsar_history_tenant ON dsar_status_history(tenant_id);

-- Create indexes for workflow queries
CREATE INDEX idx_dsar_due_at ON dsar_requests(due_at) WHERE closed_at IS NULL;
CREATE INDEX idx_dsar_assigned ON dsar_requests(assigned_to) WHERE closed_at IS NULL;

COMMENT ON TABLE dsar_status_history IS 'Append-only audit log of DSAR status transitions';
