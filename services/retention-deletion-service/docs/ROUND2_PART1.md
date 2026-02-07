# Round 2 Part 1 — Deletion Execution Persistence (Retention-Deletion Service)

## Inventory Summary (Step 0)
- deletion_requests (from V3/V5):
  - deletion_id UUID (PK), tenant_id UUID NOT NULL, subject_id UUID NOT NULL, subject_type TEXT NOT NULL
  - entity_type TEXT NOT NULL, source TEXT NOT NULL, reason TEXT NULL, status TEXT NOT NULL
  - due_at TIMESTAMPTZ NOT NULL, assigned_to UUID NULL, requires_approval BOOLEAN, approved_by UUID NULL
  - approved_at TIMESTAMPTZ NULL, proof_required BOOLEAN, closed_at TIMESTAMPTZ NULL
  - idempotency_key TEXT NULL, evidence_bundle_id UUID NULL
  - created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, metadata JSONB
- audit_events (V1): event_id UUID PK, tenant_id UUID NOT NULL, occurred_at TIMESTAMPTZ, actor_id UUID, actor_type TEXT,
  service TEXT, action TEXT, entity_type TEXT, entity_id TEXT, payload_hash TEXT, evidence_id UUID, metadata JSONB
- outbox_events (V2): outbox_id UUID PK, tenant_id UUID NOT NULL, event_id UUID UNIQUE, event_type TEXT, source_service TEXT,
  entity_type TEXT, entity_id TEXT, occurred_at TIMESTAMPTZ, payload JSONB, payload_hash TEXT, correlation_id TEXT,
  status TEXT, attempts INT, next_attempt_at TIMESTAMPTZ, last_error TEXT, created_at TIMESTAMPTZ
- Entities use @Table(schema = "deletion"). No base entity, no @Version optimistic locking, no Spring auditing annotations.

## New Tables (V7)
Migration: [src/main/resources/dd/migration/V7__round2_deletion_execution_persistence.sql](../src/main/resources/dd/migration/V7__round2_deletion_execution_persistence.sql)

1) **deletion_execution_plan**
- Immutable-ish plan snapshot per deletion request
- Idempotency keys + plan hash + JSON plan
- Constraints: UNIQUE (tenant_id, deletion_id, plan_version), UNIQUE (tenant_id, idempotency_key)

2) **deletion_system_execution**
- Per-system execution tracking with retry-ready fields
- Constraints: UNIQUE (tenant_id, plan_id, system_key, subject_ref)

3) **deletion_manual_proof_task**
- Maker-checker manual proof tasks
- Constraints: UNIQUE (tenant_id, execution_id)

4) **deletion_backup_exception**
- Exceptions for backup retention/legal hold with expiry

5) **deletion_tombstone**
- Prevent re-creation of deleted subjects
- Constraints: UNIQUE (tenant_id, subject_type, subject_ref)

## Idempotency & Constraints
- Idempotency keys are persisted in `deletion_execution_plan`.
- All FKs use `ON DELETE RESTRICT` (no silent deletes).
- Status fields use TEXT + CHECK constraints for compatibility.

## Tests
Integration tests verify:
- Flyway V7 applies
- New tables exist
- Required constraints/indexes/FKs exist (including ON DELETE RESTRICT)
- Repository round-trip with real Postgres

Run:
```
mvn test
```
