-- V5__add_dsar_hardening_columns.sql
-- Add columns for DPDP-grade DSAR workflow

SET search_path TO dsar;

-- Add new columns to dsar_requests
ALTER TABLE dsar_requests ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
ALTER TABLE dsar_requests ADD COLUMN IF NOT EXISTS details_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE dsar_requests ADD COLUMN IF NOT EXISTS close_notes TEXT;
ALTER TABLE dsar_requests ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN NOT NULL DEFAULT false;

-- Add unique constraint for idempotency (per tenant + dataPrincipalId combination)
CREATE UNIQUE INDEX idx_dsar_idempotency 
ON dsar_requests(tenant_id, data_principal_id, idempotency_key) 
WHERE idempotency_key IS NOT NULL;

-- Add indexes for efficient queries
CREATE INDEX idx_dsar_tenant_principal_created 
ON dsar_requests(tenant_id, data_principal_id, created_at DESC);

CREATE INDEX idx_dsar_tenant_status_due 
ON dsar_requests(tenant_id, status, due_at);

CREATE INDEX idx_dsar_sla_breach_check 
ON dsar_requests(due_at, status) 
WHERE closed_at IS NULL AND sla_breached = false;

-- Ensure status history index is optimized
CREATE INDEX IF NOT EXISTS idx_dsar_history_tenant_dsar 
ON dsar_status_history(tenant_id, dsar_id, changed_at DESC);

COMMENT ON COLUMN dsar_requests.idempotency_key IS 'Idempotency key for duplicate request detection';
COMMENT ON COLUMN dsar_requests.details_json IS 'Request-specific details as JSONB';
COMMENT ON COLUMN dsar_requests.close_notes IS 'Notes provided when closing the DSAR';
COMMENT ON COLUMN dsar_requests.sla_breached IS 'Flag indicating if SLA (90 days) has been breached';
