-- V6__notification_message_retry.sql
-- Add notification_messages table for retry tracking and idempotency

CREATE TABLE notification.notification_messages (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    request_id UUID NOT NULL REFERENCES notification.notification_requests(request_id),
    recipient_id VARCHAR(100) NOT NULL,
    recipient_address VARCHAR(500) NOT NULL,
    channel VARCHAR(50) NOT NULL,              -- EMAIL, SMS, WHATSAPP
    message_subject VARCHAR(500),
    message_body TEXT NOT NULL,
    message_format VARCHAR(50) NOT NULL,        -- TEXT, HTML
    message_hash VARCHAR(64) NOT NULL,          -- SHA-256 of final message (idempotency)
    
    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, QUEUED, SENT, DELIVERED, FAILED_RETRYABLE, FAILED_TERMINAL, CONSENT_BLOCKED
    provider_message_id VARCHAR(500),           -- ID from provider after send
    provider_name VARCHAR(100),                 -- SMTP, SES, etc.
    
    -- Retry tracking
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    last_error_message TEXT,
    last_error_code VARCHAR(100),
    
    -- Evidence linkage
    evidence_artifact_id VARCHAR(200),          -- ID from evidence-reporting-service
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT chk_notification_message_status CHECK (status IN ('PENDING', 'QUEUED', 'SENT', 'DELIVERED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'CONSENT_BLOCKED'))
);

CREATE INDEX idx_notification_messages_tenant ON notification.notification_messages (tenant_id);
CREATE INDEX idx_notification_messages_request ON notification.notification_messages (request_id);
CREATE INDEX idx_notification_messages_recipient ON notification.notification_messages (tenant_id, recipient_id);
CREATE INDEX idx_notification_messages_status ON notification.notification_messages (status);
CREATE INDEX idx_notification_messages_hash ON notification.notification_messages (message_hash);
CREATE INDEX idx_notification_messages_next_retry ON notification.notification_messages (next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_notification_messages_provider_msg ON notification.notification_messages (provider_message_id) WHERE provider_message_id IS NOT NULL;
CREATE INDEX idx_notification_messages_created_at ON notification.notification_messages (created_at);

-- Add unique constraint for idempotency (same message hash + recipient within short time window)
CREATE UNIQUE INDEX uq_notification_message_idempotency 
    ON notification.notification_messages (tenant_id, recipient_address, message_hash, DATE(created_at));
