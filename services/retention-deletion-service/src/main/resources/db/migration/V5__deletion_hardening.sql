-- V5__deletion_hardening.sql
-- DPDP-grade hardening for retention and deletion workflow

SET search_path TO deletion;

-- 1) Update retention_rules to add missing columns
ALTER TABLE retention_rules 
    RENAME COLUMN id TO rule_id;

ALTER TABLE retention_rules 
    ADD COLUMN IF NOT EXISTS rule_name TEXT,
    ADD COLUMN IF NOT EXISTS subject_type TEXT,
    ADD COLUMN IF NOT EXISTS created_by UUID;

UPDATE retention_rules SET rule_name = 'Legacy Rule ' || rule_id::TEXT WHERE rule_name IS NULL;
UPDATE retention_rules SET subject_type = 'CUSTOMER' WHERE subject_type IS NULL;

ALTER TABLE retention_rules 
    ALTER COLUMN rule_name SET NOT NULL,
    ALTER COLUMN subject_type SET NOT NULL;

-- Add unique constraint for rule name per tenant
CREATE UNIQUE INDEX IF NOT EXISTS idx_retention_rules_name 
    ON retention_rules(tenant_id, rule_name);

-- Add combined index for lookups
CREATE INDEX IF NOT EXISTS idx_retention_rules_subject_entity 
    ON retention_rules(tenant_id, subject_type, entity_type);

-- Update existing index
DROP INDEX IF EXISTS idx_retention_rules_enabled;
CREATE INDEX idx_retention_rules_tenant_enabled 
    ON retention_rules(tenant_id, enabled) WHERE enabled = true;

COMMENT ON COLUMN retention_rules.rule_name IS 'Unique rule name within tenant';
COMMENT ON COLUMN retention_rules.subject_type IS 'CUSTOMER|EMPLOYEE|VENDOR|OTHER';
COMMENT ON COLUMN retention_rules.action IS 'DELETE|ANONYMIZE';

-- 2) Update deletion_requests to add missing columns
ALTER TABLE deletion_requests 
    RENAME COLUMN id TO deletion_id;

ALTER TABLE deletion_requests 
    ADD COLUMN IF NOT EXISTS entity_type TEXT,
    ADD COLUMN IF NOT EXISTS source TEXT,
    ADD COLUMN IF NOT EXISTS reason TEXT,
    ADD COLUMN IF NOT EXISTS evidence_bundle_id UUID;

UPDATE deletion_requests SET entity_type = subject_type WHERE entity_type IS NULL;
UPDATE deletion_requests SET source = 'ADMIN' WHERE source IS NULL;

ALTER TABLE deletion_requests 
    ALTER COLUMN entity_type SET NOT NULL,
    ALTER COLUMN source SET NOT NULL;

-- Update idempotency index to include subject_id and entity_type
DROP INDEX IF EXISTS idx_deletion_idempotency;
CREATE UNIQUE INDEX idx_deletion_idempotency 
    ON deletion_requests(tenant_id, subject_id, entity_type, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

-- Add new indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_deletion_tenant_status_due 
    ON deletion_requests(tenant_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_deletion_tenant_subject_created 
    ON deletion_requests(tenant_id, subject_id, created_at DESC);

DROP INDEX IF EXISTS idx_deletion_tenant_status;

COMMENT ON COLUMN deletion_requests.source IS 'DSAR|RETENTION|ADMIN';
COMMENT ON COLUMN deletion_requests.entity_type IS 'Type of data entity being deleted';
COMMENT ON COLUMN deletion_requests.evidence_bundle_id IS 'Evidence bundle created on close';

-- 3) Update deletion_proofs to add size_bytes column
ALTER TABLE deletion_proofs 
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

UPDATE deletion_proofs SET size_bytes = 0 WHERE size_bytes IS NULL;

ALTER TABLE deletion_proofs 
    ALTER COLUMN size_bytes SET NOT NULL;

-- Update indexes
DROP INDEX IF EXISTS idx_deletion_proofs_deletion;
DROP INDEX IF EXISTS idx_deletion_proofs_tenant;
CREATE INDEX idx_deletion_proofs_tenant_deletion 
    ON deletion_proofs(tenant_id, deletion_id);

COMMENT ON COLUMN deletion_proofs.size_bytes IS 'File size in bytes';

-- 4) Update deletion_status_history indexes
DROP INDEX IF EXISTS idx_deletion_history_deletion;
DROP INDEX IF EXISTS idx_deletion_history_tenant;
CREATE INDEX idx_deletion_history_tenant_deletion 
    ON deletion_status_history(tenant_id, deletion_id, changed_at DESC);

-- 5) Create retention_candidates table for auto-deletion workflow
CREATE TABLE IF NOT EXISTS retention_candidates (
    candidate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    subject_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_retention_candidates_tenant_subject 
    ON retention_candidates(tenant_id, subject_id);

CREATE INDEX idx_retention_candidates_tenant_type_last_seen 
    ON retention_candidates(tenant_id, subject_type, entity_type, last_seen_at);

-- Unique constraint to prevent duplicate candidates
CREATE UNIQUE INDEX idx_retention_candidates_unique 
    ON retention_candidates(tenant_id, subject_id, entity_type);

COMMENT ON TABLE retention_candidates IS 'Subjects eligible for retention-based deletion';
COMMENT ON COLUMN retention_candidates.last_seen_at IS 'Last activity/interaction timestamp';
