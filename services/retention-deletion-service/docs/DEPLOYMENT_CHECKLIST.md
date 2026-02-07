# Deployment Checklist — Retention-Deletion Service (Round 2)

## Pre-Deploy
- Confirm Flyway enabled. Flyway locations include both classpath:db/migration and classpath:dd/migration. Versions V1–V6 live under db/migration; Round-2 V7 lives under dd/migration.
- Verify configuration:
  - Audit writes to schema deletion (entities use @Table(schema = "deletion")).
  - connector.service.url
  - evidence.service.url
  - deletion.cascade.systems configured for each entityType
  - retention.scheduler.enabled and retention.scheduler.cron
- Ensure evidence-reporting-service and connector-service endpoints are reachable.
- Ensure outbox relay/worker is running for outbox_events.
- Validate security headers at the gateway or reverse proxy (HSTS, X-Content-Type-Options, X-Frame-Options, CSP).

## Deploy
- Deploy the service with the updated configuration.
- Confirm /actuator/health is UP.

## Post-Deploy Validation
- Create a deletion and run cascade-execute with an idempotency key.
- Upload a proof and close the deletion to confirm evidence bundle creation.
- Verify audit_events and outbox_events rows are produced.
- Trigger a manual proof submit and exception grant to verify evidence artifact creation.

## Rollback
- If evidence service failures cause 503 spikes, roll back or disable close attempts.
- Revert to prior deployment and ensure database schema compatibility.
