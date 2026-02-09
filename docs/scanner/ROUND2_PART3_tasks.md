# Round 2 Part 3 — Remediation Tasks

## Overview
Part 3 adds deterministic task creation from website findings and a task lifecycle API with append-only task events. Task creation is idempotent on `finding_fingerprint` and writes audit/outbox records for critical actions.

## Task Generation
**Endpoint**: POST /scanner/runs/{runId}/tasks/generate

**Request**
```json
{
  "kinds": ["INSECURE_FORM_ACTION_HTTP","FORM_PII_DETECTED","TRACKER_DETECTED","UNKNOWN_THIRD_PARTY_ENDPOINT"],
  "defaultOwnerUserId": "uuid",
  "defaultOwnerEmail": "email",
  "forceReopenClosed": false
}
```

**Rules**
- Run must be `SUCCEEDED` or `PARTIAL`.
- Uses `scan_findings.finding_fingerprint` for idempotency.
- If an `OPEN` or `IN_PROGRESS` task already exists for a fingerprint, no new task is created.
- Closed/waived tasks are not recreated unless `forceReopenClosed=true`.
- Tracker tasks are created only if the tracker is third-party and not in `scan_sources.metadata.allowedTrackerDomains`.

**Supported website kinds**
- `INSECURE_FORM_ACTION_HTTP` → HIGH → “Fix insecure form submission (HTTP)”
- `FORM_PII_DETECTED` (requires `formAction`) → HIGH → “Review PII form collection and notice”
- `TRACKER_DETECTED` (third-party + not allowlisted) → MED → “Review tracker usage and consent gating”
- `UNKNOWN_THIRD_PARTY_ENDPOINT` → MED → “Review unknown third-party endpoints”

**Response**
```json
{
  "runId": "uuid",
  "createdCount": 2,
  "skippedCount": 1,
  "existingTaskIds": ["uuid"],
  "createdTaskIds": ["uuid","uuid"]
}
```

## Task Lifecycle
**List**: GET /scanner/tasks
- Query: `status`, `sourceId`, `runId`, `severity`, `page`, `size`

**Get**: GET /scanner/tasks/{taskId}

**Transition**: POST /scanner/tasks/{taskId}/transition
Headers: `X-User-ID`
```json
{
  "toStatus": "IN_PROGRESS|CLOSED|WAIVED|OPEN",
  "notes": "...",
  "ownerUserId": "uuid",
  "ownerEmail": "...",
  "waivedReason": "required when WAIVED",
  "closureNotes": "required when CLOSED"
}
```

**Allowed transitions**
- `OPEN` → `IN_PROGRESS`, `CLOSED`, `WAIVED`
- `IN_PROGRESS` → `CLOSED`, `WAIVED`
- `CLOSED`/`WAIVED` → no transitions

**Task events** (append-only)
- `TASK_CREATED` (from generation)
- `STATUS_CHANGED` (from transitions)
- `NOTE_ADDED` / `ATTACHMENT_ADDED` (optional)

**Add event**: POST /scanner/tasks/{taskId}/events
Headers: `X-User-ID`
```json
{
  "eventType": "NOTE_ADDED|ATTACHMENT_ADDED",
  "notes": "...",
  "attachmentRefs": ["ref1","ref2"]
}
```

## Audit + Outbox Events
- `scanner.task_created_from_finding`
  - Payload: `{runId, createdCount, skippedCount, createdTaskIds (capped), fingerprints (capped), kindCounts}`
- `scanner.task_status_changed`
  - Payload: `{taskId, fromStatus, toStatus, actorUserId, findingFingerprint, runId, sourceId}`
- `scanner.task_event_added` (optional)

## Idempotency
- Generation uses the partial unique index on `(tenant_id, finding_fingerprint)` for active tasks.
- CLOSED/WAIVED tasks are not recreated unless `forceReopenClosed=true`.
- Transition endpoints reject invalid state changes and append a `STATUS_CHANGED` event for every transition.
