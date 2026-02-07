# Operations Runbook — Retention-Deletion Service (Round 2)

## Service Health
- /actuator/health and /actuator/info are exposed.
- Validate database connectivity and Flyway migration state on startup.

## Required Configuration
- Ensure audit writes to schema deletion (entities use @Table(schema = "deletion")).
- connector.service.url
- evidence.service.url
- deletion.cascade.systems[*].systemKey and entityTypes
- retention.scheduler.enabled (disable for deterministic runs)
- retention.scheduler.cron (production only)

## Evidence Service Dependency
- Close and manual proof submit require evidence-service.
- 503 responses indicate evidence-service outage; evidence_bundle_id remains null and deletion is not finalized/closed until evidence bundle creation succeeds.
- Recommended action: restore evidence-service and retry close/manual proof submission.

## Connector Service Dependency
- Cascade execute calls connector-service to start deletion jobs.
- Connector 503 marks executions as FAILED_RETRYABLE and schedules next retry.

## Audit & Outbox
- Audit events are written to deletion.audit_events.
- Outbox events are written to outbox_events; ensure outbox relay is running.
- Expected event families: deletion.plan_created, deletion.cascade_started, deletion.system_started,
  deletion.system_failed_retryable, deletion.system_failed_terminal, deletion.system_succeeded,
  deletion.manual_proof_*, deletion.exception_granted, deletion.proof_incomplete, deletion.completed,
  deletion.tombstoned, deletion.tombstone_removed, deletion.closed.

## Manual Proof Operations
- Request manual proof to move execution to MANUAL_REQUIRED.
- Submit proof to create evidence artifact and move task to SUBMITTED.
- Approve to mark execution SUCCEEDED; reject to keep MANUAL_REQUIRED.

## Exception Operations
- Grant exception to move execution to EXCEPTION_GRANTED.
- Evidence artifact is mandatory; 503 indicates upstream outage.

## Troubleshooting
- Evidence 503 on close: verify evidence-service URL and health, then retry close.
- Cascade failures: check connector logs and retry schedule in deletion_system_execution.
- Outbox backlog: verify outbox worker/relay is running and Kafka/MSK connectivity.

## How to Diagnose a Stuck Deletion
1) Identify the latest active plan
    - Query deletion_execution_plan by tenant_id + deletion_id order by plan_version desc.
2) List executions and statuses
    - Query deletion_system_execution by tenant_id + plan_id.
3) Check proof completeness
    - SUCCEEDED requires proof_artifact_id not null
    - EXCEPTION_GRANTED requires exception_artifact_id not null
4) Check retry schedule
    - For FAILED_RETRYABLE, inspect next_retry_at and attempt_count.
5) If all executions are complete but close fails
    - Verify evidence-service health and retry close.

## Flyway
- Ensure Flyway migrations run on startup (baseline-on-migrate enabled).
- Validate V7 migration for Round 2 tables applied before production traffic.
