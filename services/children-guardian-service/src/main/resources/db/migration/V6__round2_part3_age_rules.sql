-- V6__round2_part3_age_rules.sql
-- Add region fields to children for age rule evaluation

ALTER TABLE children.children
    ADD COLUMN IF NOT EXISTS region_country_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS region_state_code VARCHAR(10);

CREATE INDEX IF NOT EXISTS idx_children_tenant_region
    ON children.children(tenant_id, region_country_code, region_state_code);
