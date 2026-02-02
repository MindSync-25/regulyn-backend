-- V6: Add credential provider metadata columns for AWS Secrets Manager integration
-- This migration adds fields to track secret provider type and resolution metadata

-- Make encrypted_value and encryption_key_ref nullable (not required for AWS provider)
ALTER TABLE connector.connector_credentials
    ALTER COLUMN encrypted_value DROP NOT NULL,
    ALTER COLUMN encryption_key_ref DROP NOT NULL;

-- Add columns to connector_credentials table
-- Using VARCHAR for secret_provider to match Hibernate @Enumerated(EnumType.STRING) behavior
ALTER TABLE connector.connector_credentials
    ADD COLUMN secret_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL_DB_ENCRYPTED',
    ADD COLUMN secret_id VARCHAR(500),
    ADD COLUMN secret_version VARCHAR(100),
    ADD COLUMN last_resolved_at TIMESTAMP,
    ADD COLUMN resolve_fail_count INTEGER NOT NULL DEFAULT 0;

-- Add constraint to ensure only valid provider values
ALTER TABLE connector.connector_credentials
    ADD CONSTRAINT chk_secret_provider 
    CHECK (secret_provider IN ('LOCAL_DB_ENCRYPTED', 'AWS_SECRETS_MANAGER', 'ENV'));

-- Create index for faster lookups by provider and secret_id
CREATE INDEX idx_credentials_provider_secret ON connector.connector_credentials(secret_provider, secret_id);

-- Create index for monitoring failed resolutions
CREATE INDEX idx_credentials_fail_count ON connector.connector_credentials(resolve_fail_count) WHERE resolve_fail_count > 0;

-- Comment
COMMENT ON COLUMN connector.connector_credentials.secret_provider IS 'Provider type: LOCAL_DB_ENCRYPTED, AWS_SECRETS_MANAGER, or ENV';
COMMENT ON COLUMN connector.connector_credentials.secret_id IS 'External secret identifier (e.g., AWS secret ARN or name)';
COMMENT ON COLUMN connector.connector_credentials.secret_version IS 'Version of the secret (null = latest)';
COMMENT ON COLUMN connector.connector_credentials.last_resolved_at IS 'Last successful credential resolution timestamp';
COMMENT ON COLUMN connector.connector_credentials.resolve_fail_count IS 'Consecutive resolution failure count';
