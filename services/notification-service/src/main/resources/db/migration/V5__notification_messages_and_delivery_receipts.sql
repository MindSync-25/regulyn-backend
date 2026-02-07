-- V5: Add notification_messages and notification_delivery_receipts tables for Round 2
SET search_path TO notification;

-- Table: notification_messages (per-recipient message rows)
CREATE TABLE notification_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    notification_request_id UUID NOT NULL,
    recipient TEXT NOT NULL,
    channel TEXT NOT NULL,
    category TEXT NOT NULL,
    template_key TEXT,
    template_id UUID,
    template_version_id UUID,
    language TEXT,
    message_hash BYTEA NOT NULL,
    message_hash_hex TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_failure_reason TEXT,
    sent_evidence_artifact_ref TEXT,
    delivered_evidence_artifact_ref TEXT,
    failed_evidence_artifact_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_notification_messages_request 
        FOREIGN KEY (notification_request_id) 
        REFERENCES notification_requests(request_id) 
        ON DELETE RESTRICT,
    
    CONSTRAINT chk_message_hash_length 
        CHECK (octet_length(message_hash) = 32),
    
    CONSTRAINT uq_notification_messages_tenant_hash 
        UNIQUE (tenant_id, message_hash_hex)
);

-- Indexes for notification_messages
CREATE INDEX idx_notification_messages_tenant_request 
    ON notification_messages(tenant_id, notification_request_id);

CREATE INDEX idx_notification_messages_tenant_recipient 
    ON notification_messages(tenant_id, recipient);

CREATE INDEX idx_notification_messages_tenant_status_next_attempt 
    ON notification_messages(tenant_id, status, next_attempt_at);

COMMENT ON TABLE notification_messages IS 'Per-recipient message rows for Round 2 delivery tracking';
COMMENT ON COLUMN notification_messages.message_hash IS 'SHA-256 hash of message content (32 bytes)';
COMMENT ON COLUMN notification_messages.message_hash_hex IS 'Hex representation of message_hash for idempotency';
COMMENT ON COLUMN notification_messages.status IS 'Message status: QUEUED, SENDING, SENT, DELIVERED, FAILED, etc.';

-- Table: notification_delivery_receipts (provider receipt rows)
CREATE TABLE notification_delivery_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    notification_message_id UUID NOT NULL,
    provider TEXT NOT NULL,
    provider_message_id TEXT,
    status TEXT NOT NULL,
    failure_reason TEXT,
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload_hash BYTEA NOT NULL,
    payload_hash_hex TEXT NOT NULL,
    receipt_payload JSONB NOT NULL,
    receipt_evidence_artifact_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_notification_delivery_receipts_message 
        FOREIGN KEY (notification_message_id) 
        REFERENCES notification_messages(id) 
        ON DELETE RESTRICT,
    
    CONSTRAINT chk_payload_hash_length 
        CHECK (octet_length(payload_hash) = 32),
    
    CONSTRAINT uq_notification_delivery_receipts_tenant_hash 
        UNIQUE (tenant_id, payload_hash_hex)
);

-- Indexes for notification_delivery_receipts
CREATE INDEX idx_notification_delivery_receipts_tenant_message 
    ON notification_delivery_receipts(tenant_id, notification_message_id);

CREATE INDEX idx_notification_delivery_receipts_tenant_status 
    ON notification_delivery_receipts(tenant_id, status);

CREATE INDEX idx_notification_delivery_receipts_tenant_provider 
    ON notification_delivery_receipts(tenant_id, provider, provider_message_id);

COMMENT ON TABLE notification_delivery_receipts IS 'Provider delivery receipts linked to messages';
COMMENT ON COLUMN notification_delivery_receipts.payload_hash IS 'SHA-256 hash of receipt payload (32 bytes)';
COMMENT ON COLUMN notification_delivery_receipts.payload_hash_hex IS 'Hex representation of payload_hash for callback idempotency';
COMMENT ON COLUMN notification_delivery_receipts.receipt_payload IS 'Full receipt payload as JSONB';
