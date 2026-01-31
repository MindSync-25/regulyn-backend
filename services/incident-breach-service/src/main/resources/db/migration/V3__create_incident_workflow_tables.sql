-- V3__create_incident_workflow_tables.sql
-- Create incident/breach workflow tables

SET search_path TO incident;

-- Incident cases
CREATE TABLE incident_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_type TEXT NOT NULL,
    status TEXT NOT NULL,
    severity TEXT NOT NULL,
    opened_at TIMESTAMPTZ DEFAULT NOW(),
    notify_due_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    assigned_to UUID,
    summary TEXT,
    approver_required BOOLEAN DEFAULT true,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    close_evidence_bundle_id UUID,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_incident_tenant_status ON incident_cases(tenant_id, status);
CREATE INDEX idx_incident_notify_due ON incident_cases(notify_due_at) WHERE closed_at IS NULL;
CREATE INDEX idx_incident_severity ON incident_cases(severity);

-- Incident tasks
CREATE TABLE incident_tasks (
    task_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    task_type TEXT NOT NULL,
    status TEXT NOT NULL,
    assigned_to UUID,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_incident_task FOREIGN KEY (incident_id) REFERENCES incident_cases(id)
);

CREATE INDEX idx_incident_tasks_incident ON incident_tasks(incident_id);
CREATE INDEX idx_incident_tasks_assigned ON incident_tasks(assigned_to) WHERE status != 'COMPLETED';

-- Incident notifications
CREATE TABLE incident_notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    channel TEXT NOT NULL,
    draft_text TEXT NOT NULL,
    status TEXT NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    dispatch_log JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_incident_notification FOREIGN KEY (incident_id) REFERENCES incident_cases(id)
);

CREATE INDEX idx_incident_notifications_incident ON incident_notifications(incident_id);
CREATE INDEX idx_incident_notifications_status ON incident_notifications(status);

COMMENT ON TABLE incident_cases IS 'Incident and breach cases with 72-hour notification SLA';
COMMENT ON TABLE incident_tasks IS 'Tasks for incident response workflow';
COMMENT ON TABLE incident_notifications IS 'Draft and sent notifications for incidents';
