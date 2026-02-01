-- V4: Vendor Sharing Domain Schema
-- Creates tables for vendor registry, agreements, sharing records, and exports

CREATE SCHEMA IF NOT EXISTS vendor;

-- 1) Vendors table
CREATE TABLE vendor.vendors (
    vendor_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    vendor_name TEXT NOT NULL,
    vendor_type TEXT NOT NULL CHECK (vendor_type IN ('PROCESSOR', 'SUB_PROCESSOR', 'SERVICE_PROVIDER', 'PARTNER', 'OTHER')),
    contact_email TEXT,
    country TEXT NOT NULL,
    hosting_region TEXT NOT NULL CHECK (hosting_region IN ('INDIA', 'US', 'EU', 'OTHER')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    risk_level TEXT NOT NULL CHECK (risk_level IN ('LOW', 'MED', 'HIGH')),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT vendors_unique_name_per_tenant UNIQUE (tenant_id, vendor_name)
);

CREATE INDEX idx_vendors_tenant_enabled ON vendor.vendors(tenant_id, enabled);
CREATE INDEX idx_vendors_tenant_risk ON vendor.vendors(tenant_id, risk_level);

-- 2) Vendor Agreements table
CREATE TABLE vendor.vendor_agreements (
    agreement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL REFERENCES vendor.vendors(vendor_id) ON DELETE CASCADE,
    agreement_type TEXT NOT NULL CHECK (agreement_type IN ('DPA', 'MSA', 'SCC', 'NDA', 'OTHER')),
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED')),
    effective_from DATE,
    effective_to DATE,
    doc_ref TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agreements_tenant_vendor ON vendor.vendor_agreements(tenant_id, vendor_id);

-- 3) Sharing Records table
CREATE TABLE vendor.sharing_records (
    sharing_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL REFERENCES vendor.vendors(vendor_id) ON DELETE RESTRICT,
    activity_id UUID,           -- reference only (from ROPA)
    system_id UUID,             -- reference only (from ROPA)
    sharing_purpose TEXT NOT NULL,
    lawful_basis TEXT NOT NULL CHECK (lawful_basis IN ('CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTERESTS', 'PUBLIC_TASK', 'LEGITIMATE_INTERESTS', 'OTHER')),
    data_categories TEXT[] NOT NULL,
    frequency TEXT NOT NULL CHECK (frequency IN ('ONE_TIME', 'ONGOING', 'PER_REQUEST')),
    transfer_cross_border BOOLEAN NOT NULL DEFAULT false,
    transfer_to_regions TEXT[],
    transfer_notes TEXT,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    enabled BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sharing_tenant_vendor_created ON vendor.sharing_records(tenant_id, vendor_id, created_at DESC);
CREATE INDEX idx_sharing_tenant_enabled ON vendor.sharing_records(tenant_id, enabled);
CREATE INDEX idx_sharing_tenant_cross_border ON vendor.sharing_records(tenant_id, transfer_cross_border);

-- 4) Sharing Status History table (append-only audit trail)
CREATE TABLE vendor.sharing_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    sharing_id UUID NOT NULL REFERENCES vendor.sharing_records(sharing_id) ON DELETE CASCADE,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by UUID,
    reason TEXT
);

CREATE INDEX idx_history_tenant_sharing ON vendor.sharing_status_history(tenant_id, sharing_id, changed_at);

-- 5) Vendor Exports table (evidence integration)
CREATE TABLE vendor.vendor_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_exports_tenant_created ON vendor.vendor_exports(tenant_id, created_at);
