-- V6__consent_round2_part4_language_snapshot_enforcement.sql
-- Round 2 Part 4: dual language snapshots + translation metadata

SET search_path TO consent;

-- Consent receipts: dual language snapshots + region
ALTER TABLE consent_receipts
    ADD COLUMN region_code VARCHAR(8) NULL,
    ADD COLUMN english_language_code VARCHAR(12) NULL DEFAULT 'en',
    ADD COLUMN english_notice_language_text_id UUID NULL,
    ADD COLUMN english_content_hash_sha256 VARCHAR(64) NULL,
    ADD COLUMN regional_notice_language_text_id UUID NULL,
    ADD COLUMN regional_content_hash_sha256 VARCHAR(64) NULL;

ALTER TABLE consent_receipts
    ADD CONSTRAINT fk_consent_receipts_english_language
    FOREIGN KEY (english_notice_language_text_id) REFERENCES notice_language_text(language_id);

ALTER TABLE consent_receipts
    ADD CONSTRAINT fk_consent_receipts_regional_language
    FOREIGN KEY (regional_notice_language_text_id) REFERENCES notice_language_text(language_id);

CREATE INDEX idx_consent_receipts_principal_notice_version
    ON consent_receipts(tenant_id, data_principal_id, version_id);

CREATE INDEX idx_consent_receipts_regional_language
    ON consent_receipts(tenant_id, version_id, regional_notice_language_text_id);

CREATE INDEX idx_consent_receipts_english_language
    ON consent_receipts(tenant_id, version_id, english_notice_language_text_id);

CREATE INDEX idx_consent_receipts_region_code
    ON consent_receipts(tenant_id, region_code);

-- Notice language translation metadata
ALTER TABLE notice_language_text
    ADD COLUMN translation_source VARCHAR(20) NULL,
    ADD COLUMN translation_engine VARCHAR(80) NULL,
    ADD COLUMN translation_engine_version VARCHAR(80) NULL,
    ADD COLUMN translated_from_language VARCHAR(12) NULL DEFAULT 'en',
    ADD COLUMN translated_at TIMESTAMPTZ NULL;
