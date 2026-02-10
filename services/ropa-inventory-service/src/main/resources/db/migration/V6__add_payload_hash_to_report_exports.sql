ALTER TABLE ropa.ropa_report_exports
    ADD COLUMN IF NOT EXISTS payload_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_rre_tenant_type_idem
    ON ropa.ropa_report_exports(tenant_id, report_type, idempotency_key);
