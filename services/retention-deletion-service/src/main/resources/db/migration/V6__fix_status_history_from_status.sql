-- V6__fix_status_history_from_status.sql
-- Fix from_status to allow NULL for initial status history entries

SET search_path TO deletion;

-- Make from_status nullable since initial transitions have no previous status
ALTER TABLE deletion_status_history 
    ALTER COLUMN from_status DROP NOT NULL;

COMMENT ON COLUMN deletion_status_history.from_status IS 'Previous status (NULL for initial creation)';
