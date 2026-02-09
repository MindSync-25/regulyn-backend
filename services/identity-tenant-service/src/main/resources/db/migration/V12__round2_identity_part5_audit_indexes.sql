-- V12__round2_identity_part5_audit_indexes.sql
-- Add indexes for audit query performance

CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_occurred_desc
  ON identity.audit_events(tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_actor_occurred_desc
  ON identity.audit_events(tenant_id, actor_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_action_occurred_desc
  ON identity.audit_events(tenant_id, action, occurred_at DESC);