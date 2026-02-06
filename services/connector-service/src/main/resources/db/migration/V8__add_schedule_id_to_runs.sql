-- V8: Add schedule_id to connector_runs for scheduled job tracking

ALTER TABLE connector.connector_runs
ADD COLUMN schedule_id UUID NULL REFERENCES connector.connector_schedules(id) ON DELETE SET NULL;

CREATE INDEX idx_connector_runs_schedule ON connector.connector_runs(schedule_id);

COMMENT ON COLUMN connector.connector_runs.schedule_id IS 'Reference to the schedule that created this run (NULL for manual runs)';
