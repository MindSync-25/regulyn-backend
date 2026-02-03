-- V7__final_hardening.sql
-- Final hardening: expand delivery_receipts, add message_category, refine status machine

-- 1. Expand delivery_receipts for complete audit trail
ALTER TABLE notification.delivery_receipts ADD COLUMN IF NOT EXISTS event_time TIMESTAMP;
ALTER TABLE notification.delivery_receipts ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE notification.delivery_receipts ADD COLUMN IF NOT EXISTS raw_payload_hash VARCHAR(64);
ALTER TABLE notification.delivery_receipts ADD COLUMN IF NOT EXISTS raw_payload_ref TEXT;
ALTER TABLE notification.delivery_receipts ADD COLUMN IF NOT EXISTS provider_signature_valid BOOLEAN DEFAULT NULL;

-- 2. Add message_category to notification_requests for consent bypass rules
ALTER TABLE notification.notification_requests ADD COLUMN IF NOT EXISTS message_category VARCHAR(50) DEFAULT 'MARKETING';
UPDATE notification.notification_requests SET message_category = 'MARKETING' WHERE message_category IS NULL;
ALTER TABLE notification.notification_requests ALTER COLUMN message_category SET NOT NULL;

-- 3. Add constraint for message_category
ALTER TABLE notification.notification_requests 
    ADD CONSTRAINT chk_message_category CHECK (message_category IN ('LEGAL_MANDATORY', 'MARKETING', 'PRODUCT'));

-- 4. Refine notification_messages status values (making them explicit)
-- Update existing FAILED to FAILED_RETRYABLE for retry processing
UPDATE notification.notification_messages SET status = 'FAILED_RETRYABLE' WHERE status = 'FAILED';

-- Add constraint for explicit status machine
ALTER TABLE notification.notification_messages DROP CONSTRAINT IF EXISTS chk_notification_message_status;
ALTER TABLE notification.notification_messages 
    ADD CONSTRAINT chk_notification_message_status CHECK (
        status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'CONSENT_BLOCKED')
    );

-- 5. Create index for event_time on delivery_receipts
CREATE INDEX IF NOT EXISTS idx_delivery_receipts_event_time ON notification.delivery_receipts (event_time);
