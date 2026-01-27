-- V5__create_api_keys_table.sql
CREATE TABLE api_keys (
    api_key_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    CONSTRAINT fk_api_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT uq_api_keys_hash UNIQUE (api_key_hash)
);

CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX idx_api_keys_hash ON api_keys(api_key_hash);
CREATE INDEX idx_api_keys_enabled ON api_keys(enabled);

COMMENT ON TABLE api_keys IS 'API keys for connector agents and service-to-service auth';
COMMENT ON COLUMN api_keys.api_key_hash IS 'SHA-256 hash of the API key - never store plaintext';
