-- ROPA Inventory Domain Schema
SET search_path TO ropa;

-- Systems / Data Stores table
CREATE TABLE ropa_systems (
    system_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    system_name TEXT NOT NULL,
    system_type TEXT NOT NULL CHECK (system_type IN ('APP', 'DATABASE', 'SAAS', 'FILESTORE', 'OTHER')),
    owner_team TEXT,
    location TEXT NOT NULL CHECK (location IN ('INDIA', 'US', 'EU', 'OTHER')),
    criticality TEXT NOT NULL CHECK (criticality IN ('LOW', 'MED', 'HIGH')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ropa_systems_tenant_name UNIQUE (tenant_id, system_name)
);

CREATE INDEX idx_ropa_systems_tenant_type ON ropa_systems(tenant_id, system_type);
CREATE INDEX idx_ropa_systems_tenant_enabled ON ropa_systems(tenant_id, enabled);

-- Data Categories table
CREATE TABLE ropa_data_categories (
    data_category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    category_key TEXT NOT NULL CHECK (category_key IN ('PII', 'FINANCIAL', 'HEALTH', 'BIOMETRIC', 'CHILD_DATA', 'EMPLOYEE_DATA', 'DEVICE', 'OTHER')),
    label TEXT NOT NULL,
    sensitive BOOLEAN NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ropa_data_categories_tenant_key UNIQUE (tenant_id, category_key)
);

CREATE INDEX idx_ropa_data_categories_tenant ON ropa_data_categories(tenant_id);

-- Activity Versions table (versioned processing activities)
CREATE TABLE ropa_activity_versions (
    version_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    version_number INT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    activity_name TEXT NOT NULL,
    purpose TEXT NOT NULL,
    lawful_basis TEXT NOT NULL CHECK (lawful_basis IN ('CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTERESTS', 'PUBLIC_TASK', 'LEGITIMATE_INTERESTS', 'OTHER')),
    data_principal_type TEXT NOT NULL CHECK (data_principal_type IN ('CUSTOMER', 'EMPLOYEE', 'VENDOR', 'CHILD', 'OTHER')),
    description TEXT,
    retention_policy TEXT,
    retention_days INT CHECK (retention_days >= 0 AND retention_days <= 36500),
    risk_level TEXT NOT NULL CHECK (risk_level IN ('LOW', 'MED', 'HIGH')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ropa_activity_versions_tenant_activity_version UNIQUE (tenant_id, activity_id, version_number)
);

CREATE INDEX idx_ropa_activity_versions_tenant_status ON ropa_activity_versions(tenant_id, status);
CREATE INDEX idx_ropa_activity_versions_tenant_risk ON ropa_activity_versions(tenant_id, risk_level);
CREATE INDEX idx_ropa_activity_versions_tenant_lawful_basis ON ropa_activity_versions(tenant_id, lawful_basis);
CREATE INDEX idx_ropa_activity_versions_activity_id ON ropa_activity_versions(tenant_id, activity_id);

-- Activity Links table (link metadata)
CREATE TABLE ropa_activity_links (
    link_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    version_id UUID NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ropa_activity_links_version FOREIGN KEY (version_id) REFERENCES ropa_activity_versions(version_id) ON DELETE CASCADE
);

CREATE INDEX idx_ropa_activity_links_tenant_activity ON ropa_activity_links(tenant_id, activity_id);

-- Activity Systems junction table
CREATE TABLE ropa_activity_systems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    system_id UUID NOT NULL,
    CONSTRAINT fk_ropa_activity_systems_version FOREIGN KEY (version_id) REFERENCES ropa_activity_versions(version_id) ON DELETE CASCADE,
    CONSTRAINT fk_ropa_activity_systems_system FOREIGN KEY (system_id) REFERENCES ropa_systems(system_id) ON DELETE CASCADE,
    CONSTRAINT uq_ropa_activity_systems_tenant_version_system UNIQUE (tenant_id, version_id, system_id)
);

CREATE INDEX idx_ropa_activity_systems_version ON ropa_activity_systems(version_id);
CREATE INDEX idx_ropa_activity_systems_system ON ropa_activity_systems(system_id);

-- Activity Data Categories junction table
CREATE TABLE ropa_activity_data_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    data_category_id UUID NOT NULL,
    CONSTRAINT fk_ropa_activity_data_categories_version FOREIGN KEY (version_id) REFERENCES ropa_activity_versions(version_id) ON DELETE CASCADE,
    CONSTRAINT fk_ropa_activity_data_categories_category FOREIGN KEY (data_category_id) REFERENCES ropa_data_categories(data_category_id) ON DELETE CASCADE,
    CONSTRAINT uq_ropa_activity_data_categories_tenant_version_category UNIQUE (tenant_id, version_id, data_category_id)
);

CREATE INDEX idx_ropa_activity_data_categories_version ON ropa_activity_data_categories(version_id);
CREATE INDEX idx_ropa_activity_data_categories_category ON ropa_activity_data_categories(data_category_id);

-- Activity Vendors junction table (vendor_id is just a reference, no FK)
CREATE TABLE ropa_activity_vendors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    CONSTRAINT fk_ropa_activity_vendors_version FOREIGN KEY (version_id) REFERENCES ropa_activity_versions(version_id) ON DELETE CASCADE,
    CONSTRAINT uq_ropa_activity_vendors_tenant_version_vendor UNIQUE (tenant_id, version_id, vendor_id)
);

CREATE INDEX idx_ropa_activity_vendors_version ON ropa_activity_vendors(version_id);

-- ROPA Exports table
CREATE TABLE ropa_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ropa_exports_tenant_created ON ropa_exports(tenant_id, created_at);
