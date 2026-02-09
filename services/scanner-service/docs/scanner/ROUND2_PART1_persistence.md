# Scanner Service — Round 2 — Part 1 (Persistence Only)

## Purpose
This round adds persistence primitives for website scanning, remediation tasks, and evidence references. There is **no** crawler implementation, workflow endpoints, or evidence-service integration in Part 1.

## New Tables

### scanner.scanned_pages
Stores per-page crawl summaries (no HTML storage). Key fields include normalized URL hash, status, timing, and a JSON summary of data collection signals.

**Idempotency**
- Unique per run: `(tenant_id, run_id, url_hash)`

### scanner.remediation_tasks
Stores actionable remediation items derived from findings.

**Status/Severity Checks**
- `severity` in `LOW | MED | HIGH | CRITICAL`
- `status` in `OPEN | IN_PROGRESS | CLOSED | WAIVED`

**Idempotency**
- Partial unique index prevents duplicate active tasks:
  - `(tenant_id, finding_fingerprint)` where `status IN ('OPEN','IN_PROGRESS')`

### scanner.remediation_task_events
Append-only event log for remediation task changes, ensuring all changes are auditable.

### scanner.scan_run_evidence_refs
Links a scan run to an immutable evidence bundle reference.

**Note**
- Evidence wiring is implemented in Part 4.

## scan_findings Extensions
New fingerprinting and key-attribute columns added to `scanner.scan_findings`:
- `finding_fingerprint` (nullable)
- `finding_fingerprint_version` (default 1)
- `normalized_subject`
- `key_attributes` (JSONB)

**Idempotency**
- Unique per run when fingerprint present: `(tenant_id, run_id, finding_fingerprint)`

**Important**
- Fingerprints are **not backfilled** in Part 1.

## Migration
Flyway migration:
- `V5__scanner_round2_part1_persistence.sql`

## Compatibility
- All changes are backward-compatible.
- No existing endpoints, tables, or entities are renamed or removed.