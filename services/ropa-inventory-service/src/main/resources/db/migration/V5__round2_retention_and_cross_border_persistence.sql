-- Round 2: Retention + Cross-border persistence
SET search_path TO ropa;

-- A) Retention policy tables
CREATE TABLE retention_policies_system (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    system_id UUID NOT NULL,
    retention_days INT NOT NULL,
    retention_basis TEXT NOT NULL,
    retention_note TEXT,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rps_tenant_system UNIQUE (tenant_id, system_id),
    CONSTRAINT chk_rps_retention_days CHECK (retention_days > 0),
    CONSTRAINT chk_rps_retention_basis CHECK (retention_basis IN ('LEGAL_OBLIGATION','CONTRACT','CONSENT','LEGIT_INTEREST','OTHER'))
);

CREATE UNIQUE INDEX ux_rps_tenant_idempotency ON retention_policies_system(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_rps_tenant_system ON retention_policies_system(tenant_id, system_id);

CREATE TABLE retention_policies_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    retention_days INT NOT NULL,
    retention_basis TEXT NOT NULL,
    retention_note TEXT,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rpa_tenant_activity UNIQUE (tenant_id, activity_id),
    CONSTRAINT chk_rpa_retention_days CHECK (retention_days > 0),
    CONSTRAINT chk_rpa_retention_basis CHECK (retention_basis IN ('LEGAL_OBLIGATION','CONTRACT','CONSENT','LEGIT_INTEREST','OTHER'))
);

CREATE UNIQUE INDEX ux_rpa_tenant_idempotency ON retention_policies_activity(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_rpa_tenant_activity ON retention_policies_activity(tenant_id, activity_id);

CREATE TABLE retention_policies_category_purpose (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_category_id UUID NOT NULL,
    purpose_version_id UUID NOT NULL,
    retention_days INT NOT NULL,
    retention_basis TEXT NOT NULL,
    retention_note TEXT,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rpcp_tenant_cat_purpose UNIQUE (tenant_id, data_category_id, purpose_version_id),
    CONSTRAINT chk_rpcp_retention_days CHECK (retention_days > 0),
    CONSTRAINT chk_rpcp_retention_basis CHECK (retention_basis IN ('LEGAL_OBLIGATION','CONTRACT','CONSENT','LEGIT_INTEREST','OTHER'))
);

CREATE UNIQUE INDEX ux_rpcp_tenant_idempotency ON retention_policies_category_purpose(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_rpcp_tenant_cat_purpose ON retention_policies_category_purpose(tenant_id, data_category_id, purpose_version_id);

-- B) Cross-border transfers
CREATE TABLE cross_border_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    system_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    source_region TEXT NOT NULL,
    destination_region TEXT NOT NULL,
    transfer_mechanism TEXT NOT NULL,
    legal_basis TEXT NOT NULL,
    frequency TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cbt_tenant_activity_vendor_regions_mech UNIQUE (tenant_id, activity_id, vendor_id, source_region, destination_region, transfer_mechanism),
    CONSTRAINT chk_cbt_transfer_mechanism CHECK (transfer_mechanism IN ('SCC','BCR','CONSENT','LEGAL_REQUIREMENT','OTHER')),
    CONSTRAINT chk_cbt_frequency CHECK (frequency IN ('ONE_TIME','CONTINUOUS','PERIODIC'))
);

CREATE UNIQUE INDEX ux_cbt_tenant_idempotency ON cross_border_transfers(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_cbt_tenant_vendor ON cross_border_transfers(tenant_id, vendor_id);
CREATE INDEX idx_cbt_tenant_activity ON cross_border_transfers(tenant_id, activity_id);
CREATE INDEX idx_cbt_tenant_system ON cross_border_transfers(tenant_id, system_id);
CREATE INDEX idx_cbt_tenant_src ON cross_border_transfers(tenant_id, source_region);
CREATE INDEX idx_cbt_tenant_dst ON cross_border_transfers(tenant_id, destination_region);

CREATE TABLE cross_border_transfer_data_categories (
    transfer_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    data_category_id UUID NOT NULL,
    CONSTRAINT pk_cbt_data_categories PRIMARY KEY (transfer_id, data_category_id),
    CONSTRAINT fk_cbt_dc_transfer FOREIGN KEY (transfer_id) REFERENCES cross_border_transfers(id) ON DELETE CASCADE
);

CREATE INDEX idx_cbt_dc_tenant_category ON cross_border_transfer_data_categories(tenant_id, data_category_id);

CREATE TABLE cross_border_transfer_purpose_versions (
    transfer_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    purpose_version_id UUID NOT NULL,
    CONSTRAINT pk_cbt_purpose_versions PRIMARY KEY (transfer_id, purpose_version_id),
    CONSTRAINT fk_cbt_pv_transfer FOREIGN KEY (transfer_id) REFERENCES cross_border_transfers(id) ON DELETE CASCADE
);

CREATE INDEX idx_cbt_pv_tenant_purpose ON cross_border_transfer_purpose_versions(tenant_id, purpose_version_id);

-- C) Round-2 report exports
CREATE TABLE ropa_report_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    report_type TEXT NOT NULL,
    status TEXT NOT NULL,
    artifact_ref TEXT,
    artifact_hash TEXT,
    requested_by UUID,
    idempotency_key TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ,
    error_code TEXT,
    error_message TEXT,
    CONSTRAINT chk_rre_report_type CHECK (report_type IN ('RETENTION_MATRIX','CROSS_BORDER_REPORT')),
    CONSTRAINT chk_rre_status CHECK (status IN ('REQUESTED','CREATED','FAILED'))
);

CREATE UNIQUE INDEX ux_rre_tenant_type_idempotency ON ropa_report_exports(tenant_id, report_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_rre_tenant_type ON ropa_report_exports(tenant_id, report_type);
CREATE INDEX idx_rre_tenant_requested ON ropa_report_exports(tenant_id, requested_at);
