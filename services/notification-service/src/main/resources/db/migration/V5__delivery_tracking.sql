-- V5__delivery_tracking.sql
-- Add delivery tracking table for provider callbacks and status updates

CREATE TABLE notification.delivery_receipts (
    receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    dispatch_id UUID NOT NULL REFERENCES notification.notification_dispatch_logs(dispatch_id),
    provider_message_id VARCHAR(500) NOT NULL,  -- Provider's message identifier
    delivery_status VARCHAR(50) NOT NULL,       -- QUEUED, SENT, DELIVERED, FAILED, BOUNCED
    provider_name VARCHAR(100) NOT NULL,        -- SMTP, SES, SendGrid, etc.
    provider_event_type VARCHAR(100),           -- Bounce, Complaint, Delivery, etc.
    provider_callback_data JSONB,               -- Full webhook/callback payload
    error_message TEXT,
    error_code VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_delivery_status CHECK (delivery_status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'COMPLAINED'))
);

CREATE INDEX idx_delivery_receipts_tenant ON notification.delivery_receipts (tenant_id);
CREATE INDEX idx_delivery_receipts_dispatch ON notification.delivery_receipts (dispatch_id);
CREATE INDEX idx_delivery_receipts_provider_msg ON notification.delivery_receipts (provider_message_id);
CREATE INDEX idx_delivery_receipts_status ON notification.delivery_receipts (delivery_status);
CREATE INDEX idx_delivery_receipts_updated_at ON notification.delivery_receipts (updated_at);
