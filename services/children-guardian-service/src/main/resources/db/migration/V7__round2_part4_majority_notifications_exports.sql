-- V7__round2_part4_majority_notifications_exports.sql
-- Majority transitions, notifications, evidence export tracking

ALTER TABLE children.children
    ADD COLUMN IF NOT EXISTS guardian_authority_revoked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE children.child_majority_transitions
    ADD COLUMN IF NOT EXISTS last_notification_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS last_notification_error TEXT,
    ADD COLUMN IF NOT EXISTS last_notification_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS adult_consent_receipt_ref VARCHAR(200),
    ADD COLUMN IF NOT EXISTS adult_consent_receipt_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS adult_consent_recorded_at TIMESTAMPTZ;

ALTER TABLE children.children_evidence_exports
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_children_guardian_authority
    ON children.children(tenant_id, guardian_authority_revoked);

CREATE INDEX IF NOT EXISTS idx_child_majority_transition_status
    ON children.child_majority_transitions(tenant_id, transition_status, majority_date);
