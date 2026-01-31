-- V4__evidence_bundles.sql
-- Create tables for evidence bundles, exports, and integrity verification

-- Evidence bundles table (immutable evidence bundles)
CREATE TABLE evidence.evidence_bundles (
    bundle_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_type VARCHAR(50) NOT NULL CHECK (bundle_type IN ('DSAR', 'DELETION', 'INCIDENT', 'NOMINEE', 'GUARDIAN', 'AUDIT_EXPORT')),
    reference_type VARCHAR(50) NOT NULL CHECK (reference_type IN ('DSAR', 'DELETION', 'INCIDENT', 'NOMINEE', 'GUARDIAN', 'PERIOD')),
    reference_id VARCHAR(255) NOT NULL,
    title VARCHAR(500),
    description TEXT,
    manifest_json JSONB NOT NULL,
    bundle_hash VARCHAR(64) NOT NULL,  -- SHA-256 hash
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED', 'EXPORTED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);

-- Bundle items table (evidence and artifacts in bundles)
CREATE TABLE evidence.evidence_bundle_items (
    item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL REFERENCES evidence.evidence_bundles(bundle_id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('EVIDENCE', 'ARTIFACT')),
    evidence_id UUID,  -- references evidence_records.evidence_id
    artifact_id UUID,  -- references artifacts in file system
    item_hash VARCHAR(64) NOT NULL,  -- SHA-256 hash
    item_meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_item_reference CHECK (
        (item_type = 'EVIDENCE' AND evidence_id IS NOT NULL AND artifact_id IS NULL) OR
        (item_type = 'ARTIFACT' AND artifact_id IS NOT NULL)
    )
);

-- Evidence exports table (export packages for legal/compliance)
CREATE TABLE evidence.evidence_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL REFERENCES evidence.evidence_bundles(bundle_id) ON DELETE CASCADE,
    export_path VARCHAR(1000) NOT NULL,
    export_hash VARCHAR(64) NOT NULL,  -- SHA-256 hash of ZIP
    status VARCHAR(50) NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    failure_reason TEXT
);

-- Indexes for performance
CREATE INDEX idx_bundles_tenant_reference ON evidence.evidence_bundles(tenant_id, reference_type, reference_id);
CREATE INDEX idx_bundles_tenant_created ON evidence.evidence_bundles(tenant_id, created_at DESC);
CREATE INDEX idx_bundle_items_tenant_bundle ON evidence.evidence_bundle_items(tenant_id, bundle_id);
CREATE INDEX idx_bundle_items_evidence ON evidence.evidence_bundle_items(evidence_id) WHERE evidence_id IS NOT NULL;
CREATE INDEX idx_bundle_items_artifact ON evidence.evidence_bundle_items(artifact_id) WHERE artifact_id IS NOT NULL;
CREATE INDEX idx_exports_tenant_bundle ON evidence.evidence_exports(tenant_id, bundle_id);
CREATE INDEX idx_exports_tenant_created ON evidence.evidence_exports(tenant_id, created_at DESC);

COMMENT ON TABLE evidence.evidence_bundles IS 'Immutable evidence bundles for legal compliance (DSAR, deletion, incidents)';
COMMENT ON TABLE evidence.evidence_bundle_items IS 'Items (evidence records and artifacts) within bundles';
COMMENT ON TABLE evidence.evidence_exports IS 'Export packages of bundles for legal/compliance delivery';
