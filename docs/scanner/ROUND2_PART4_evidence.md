# Round 2 Part 4 — Evidence Artifacts + Run Bundles

## Task Evidence on Close/Waive
When a remediation task transitions to `CLOSED` or `WAIVED`, the service creates an immutable evidence artifact through evidence-reporting-service.

**Endpoint (existing transition)**: POST /scanner/tasks/{taskId}/transition

**Behavior**
- Builds a closure evidence payload (task snapshot + finding snapshot + hashes).
- Calls evidence-reporting-service `/evidence` with type:
  - `TASK_CLOSED_PROOF` or `TASK_WAIVED_PROOF`.
- Stores `evidence_artifact_ref` on the task.
- Emits audit + outbox: `scanner.task_evidence_artifact_stored`.
- Idempotent: if the task already has `evidence_artifact_ref`, no new artifact is created.

**Evidence payload highlights**
- `findingSnapshotHash`: $\mathrm{sha256}$(canonical JSON of finding snapshot)
- `closureNotesHash`: existing column value
- `taskStateHash`: $\mathrm{sha256}$(canonical JSON of closure fields + status)

## Run Evidence Bundle
**Endpoint**: POST /scanner/runs/{runId}/evidence/bundle

**Headers**: `X-Tenant-ID`, `X-User-ID`, optional `X-Idempotency-Key`

**Response**
```json
{
  "runId": "uuid",
  "bundleArtifactRef": "...",
  "bundleHash": "...",
  "status": "CREATED|ALREADY_EXISTS"
}
```

**Rules**
- Run must be `SUCCEEDED` or `PARTIAL`.
- Idempotent per `(tenant_id, run_id)` via `scan_run_evidence_refs`.

**Bundle contents**
- Run metadata + source metadata snapshot
- Scanned pages summaries (no HTML)
- Findings list (fingerprints + key attributes)
- Tasks linked to the run (status, severity, timestamps, closure hashes)
- Closure proof refs for CLOSED/WAIVED tasks

**Hashing**
`bundleHash = sha256(canonical JSON of bundle payload)`

## Audit + Outbox Events
- `scanner.task_evidence_artifact_stored`
- `scanner.scan_evidence_bundle_created`

## Operational Notes
- Evidence creation happens before final task transition for `CLOSED/WAIVED`.
- Evidence bundle creation does not mutate scan run state.
- If evidence-reporting-service is unavailable, the transition will fail and no task status change is persisted.
