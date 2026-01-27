-- V3__create_roles_table.sql
CREATE TABLE roles (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, role_name)
);

CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);

COMMENT ON TABLE roles IS 'RBAC roles - tenant-scoped';
COMMENT ON COLUMN roles.role_name IS 'Values: TENANT_ADMIN, DPO, REVIEWER, OPERATOR, AUDITOR, DATA_PRINCIPAL, CONNECTOR_AGENT';
