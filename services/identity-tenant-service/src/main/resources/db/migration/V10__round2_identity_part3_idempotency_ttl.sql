-- V10__round2_identity_part3_idempotency_ttl.sql
-- Add TTL to idempotency keys

ALTER TABLE IF EXISTS identity.idempotency_keys
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '7 days');

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at
    ON identity.idempotency_keys (expires_at);
