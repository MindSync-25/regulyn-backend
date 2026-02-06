-- V9__add_evidence_artifact_ref_to_runs.sql
-- Add evidence artifact reference for connector run results

ALTER TABLE connector.connector_runs
    ADD COLUMN IF NOT EXISTS evidence_artifact_ref VARCHAR(128);
