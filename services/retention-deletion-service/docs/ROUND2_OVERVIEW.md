# Round 2 Overview — Retention-Deletion Service

## Scope
Round 2 extends deletion execution tracking, cascade orchestration, manual proof handling, exception grants, and tombstones with audit/outbox coverage.

## Key Capabilities
- Cascade execution plans per deletion request with idempotent creation.
- System-level execution tracking (per system and subject ref).
- Manual proof tasks (request → submit → approve/reject) with evidence artifacts.
- Exception grants for backup retention/legal hold with evidence artifacts.
- Tombstones for preventing re-creation of deleted subjects.
- Evidence bundles produced on close, with explicit 503 behavior when the evidence service is unavailable.

## Storage & Migrations
- Flyway is enabled and required for boot.
- Flyway locations include both classpath:db/migration and classpath:dd/migration. Versions V1–V6 live under db/migration; Round-2 V7 lives under dd/migration.
- Audit events are stored in the deletion schema; outbox events are stored in outbox_events.

## Operational Contracts
- Only external HTTP integrations are connector-service and evidence-service.
- Outbox events are produced for cascade, manual proof, exception, and tombstone actions.
- Evidence service downtime yields 503 responses on close and artifact creation paths. evidence_bundle_id remains null and deletion is not finalized/closed until evidence bundle creation succeeds.

## Deterministic Testing
- Scheduler is disabled for integration tests and replaced with direct triggers.
- Testcontainers Postgres and WireMock are used for deterministic E2E runs.
