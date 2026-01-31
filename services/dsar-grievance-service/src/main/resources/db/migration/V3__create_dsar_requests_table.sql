-- V3__create_dsar_requests_table.sql
CREATE TABLE dsar_requests (
    request_id_pk UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    request_id TEXT NOT NULL UNIQUE,
    request_type TEXT NOT NULL,
    status TEXT NOT NULL,
    requester_email TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE INDEX idx_dsar_tenant_status ON dsar_requests(tenant_id, status);
CREATE INDEX idx_dsar_request_id ON dsar_requests(request_id);
CREATE INDEX idx_dsar_created ON dsar_requests(created_at DESC);

COMMENT ON TABLE dsar_requests IS 'Data Subject Access Requests';
