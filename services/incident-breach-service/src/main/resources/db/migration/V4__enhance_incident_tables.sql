-- V4__enhance_incident_tables.sql
-- Add missing columns and status history table

SET search_path TO incident;

-- Add missing columns to incident_cases
ALTER TABLE incident_cases 
    ADD COLUMN IF NOT EXISTS closure_notes TEXT,
    ADD COLUMN IF NOT EXISTS notify_overdue BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS evidence_bundle_id UUID;

-- Rename close_evidence_bundle_id to match spec
ALTER TABLE incident_cases DROP COLUMN IF EXISTS close_evidence_bundle_id;

-- Update indexes
CREATE INDEX IF NOT EXISTS idx_incident_tenant_notify_due ON incident_cases(tenant_id, notify_due_at);
CREATE INDEX IF NOT EXISTS idx_incident_tenant_opened ON incident_cases(tenant_id, opened_at DESC);

-- Add missing columns to incident_notifications
ALTER TABLE incident_notifications 
    ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;

-- Update notification indexes
CREATE INDEX IF NOT EXISTS idx_notification_tenant_incident ON incident_notifications(tenant_id, incident_id);
CREATE INDEX IF NOT EXISTS idx_notification_tenant_status ON incident_notifications(tenant_id, status);

-- Update task indexes
CREATE INDEX IF NOT EXISTS idx_task_tenant_incident ON incident_tasks(tenant_id, incident_id);

-- Create incident_status_history table (append-only)
CREATE TABLE IF NOT EXISTS incident_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ DEFAULT NOW(),
    changed_by UUID,
    reason TEXT,
    CONSTRAINT fk_incident_history FOREIGN KEY (incident_id) REFERENCES incident_cases(id)
);

CREATE INDEX IF NOT EXISTS idx_history_tenant_incident_changed ON incident_status_history(tenant_id, incident_id, changed_at);

COMMENT ON TABLE incident_status_history IS 'Append-only audit log of incident status transitions';
