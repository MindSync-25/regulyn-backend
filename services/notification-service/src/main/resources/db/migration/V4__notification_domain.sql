-- V4__notification_domain.sql
-- Notification Domain Tables for Versioned Templates, Language Variants, Preferences, and Dispatch Logging

-- 1. notification_templates: Core template metadata
CREATE TABLE notification.notification_templates (
    template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    template_key VARCHAR(50) NOT NULL,  -- BREACH_NOTICE, DSAR_REMINDER, CONSENT_UPDATE, GENERIC
    category VARCHAR(50) NOT NULL,       -- LEGAL, MARKETING, SECURITY, OPERATIONS
    default_language VARCHAR(10) NOT NULL,
    title VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active_version_id UUID,              -- FK to notification_template_versions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT uq_notification_template_key UNIQUE (tenant_id, template_key)
);

CREATE INDEX idx_notification_templates_tenant ON notification.notification_templates (tenant_id);
CREATE INDEX idx_notification_templates_enabled ON notification.notification_templates (tenant_id, enabled);

-- 2. notification_template_versions: Version history with DRAFT/PUBLISHED/RETIRED status
CREATE TABLE notification.notification_template_versions (
    version_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    template_id UUID NOT NULL REFERENCES notification.notification_templates(template_id),
    version_number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',  -- DRAFT, PUBLISHED, RETIRED
    change_summary TEXT,
    published_at TIMESTAMP,
    retired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT uq_notification_template_version UNIQUE (tenant_id, template_id, version_number)
);

CREATE INDEX idx_notification_template_versions_template ON notification.notification_template_versions (template_id);
CREATE INDEX idx_notification_template_versions_status ON notification.notification_template_versions (tenant_id, template_id, status);

-- 3. notification_template_language: Language variants with subject/body/format
CREATE TABLE notification.notification_template_language (
    language_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    version_id UUID NOT NULL REFERENCES notification.notification_template_versions(version_id),
    language VARCHAR(10) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    format VARCHAR(50) NOT NULL,  -- TEXT, HTML
    content_hash VARCHAR(64) NOT NULL,  -- SHA-256 of subject+body
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT uq_notification_template_language UNIQUE (tenant_id, version_id, language)
);

CREATE INDEX idx_notification_template_language_version ON notification.notification_template_language (version_id);
CREATE INDEX idx_notification_template_language_hash ON notification.notification_template_language (content_hash);

-- 4. communication_preferences: Opt-out preferences per data principal
CREATE TABLE notification.communication_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    data_principal_id VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,       -- EMAIL, SMS, WHATSAPP
    category VARCHAR(50) NOT NULL,       -- LEGAL, MARKETING, SECURITY, OPERATIONS
    opted_out BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT uq_communication_preferences UNIQUE (tenant_id, data_principal_id, channel, category)
);

CREATE INDEX idx_communication_preferences_dp ON notification.communication_preferences (tenant_id, data_principal_id);
CREATE INDEX idx_communication_preferences_opted_out ON notification.communication_preferences (tenant_id, data_principal_id, opted_out);

-- 5. notification_requests: Centralized send requests with audience and variables
CREATE TABLE notification.notification_requests (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    request_ref VARCHAR(200),            -- Optional idempotency key
    template_id UUID NOT NULL REFERENCES notification.notification_templates(template_id),
    version_id UUID NOT NULL REFERENCES notification.notification_template_versions(version_id),
    language VARCHAR(10) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    audience_type VARCHAR(50) NOT NULL,  -- BOARD, ALL_USERS, DATA_PRINCIPAL, USER_IDS
    audience_data_principal_id VARCHAR(100),
    audience_user_ids TEXT,              -- JSON array
    variables JSONB,                     -- Variable substitution map
    total_recipients INTEGER NOT NULL,
    sent_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    
    CONSTRAINT uq_notification_request_ref UNIQUE (tenant_id, request_ref)
);

CREATE INDEX idx_notification_requests_tenant ON notification.notification_requests (tenant_id);
CREATE INDEX idx_notification_requests_template ON notification.notification_requests (template_id);
CREATE INDEX idx_notification_requests_created_at ON notification.notification_requests (created_at);

-- 6. notification_dispatch_logs: Detailed dispatch logs per recipient with message hash
CREATE TABLE notification.notification_dispatch_logs (
    dispatch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    request_id UUID NOT NULL REFERENCES notification.notification_requests(request_id),
    recipient_id VARCHAR(100) NOT NULL,
    recipient_address VARCHAR(500),      -- email/phone
    status VARCHAR(50) NOT NULL,         -- SENT, SKIPPED_OPT_OUT, FAILED
    skip_reason TEXT,
    message_subject VARCHAR(500),
    message_body TEXT,
    message_hash VARCHAR(64),            -- SHA-256 of final message
    dispatched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_notification_dispatch_status CHECK (status IN ('SENT', 'SKIPPED_OPT_OUT', 'FAILED'))
);

CREATE INDEX idx_notification_dispatch_logs_request ON notification.notification_dispatch_logs (request_id);
CREATE INDEX idx_notification_dispatch_logs_recipient ON notification.notification_dispatch_logs (tenant_id, recipient_id);
CREATE INDEX idx_notification_dispatch_logs_status ON notification.notification_dispatch_logs (status);
CREATE INDEX idx_notification_dispatch_logs_dispatched_at ON notification.notification_dispatch_logs (dispatched_at);

-- Add FK constraint for active_version_id (deferred to avoid circular dependency)
ALTER TABLE notification.notification_templates
    ADD CONSTRAINT fk_notification_templates_active_version
    FOREIGN KEY (active_version_id)
    REFERENCES notification.notification_template_versions(version_id);
