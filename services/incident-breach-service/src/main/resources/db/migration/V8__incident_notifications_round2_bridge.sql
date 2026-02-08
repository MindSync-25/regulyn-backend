-- V8__incident_notifications_round2_bridge.sql
-- Add Round-2 draft bridge mapping to incident_notifications

SET search_path TO incident;

ALTER TABLE incident_notifications
    ADD COLUMN IF NOT EXISTS round2_draft_id UUID;

CREATE INDEX IF NOT EXISTS idx_incident_notifications_round2_draft_id
    ON incident_notifications(round2_draft_id);
