# Round 2 API — Retention-Deletion Service

## Common Headers
- X-Tenant-ID: UUID (required)
- X-User-ID: UUID (required)
- X-Idempotency-Key: string
	- Required for: POST /deletions/{deletionId}/cascade-execute
	- Optional for: POST /deletions (idempotent creation)

## Deletions
### Create
POST /deletions
- Body: CreateDeletionRequest
- Uses X-Idempotency-Key for idempotent creation
Response codes:
- 200 OK
- 409 Conflict (workflow gating)
- 404 Not Found

### Cascade Execute
POST /deletions/{deletionId}/cascade-execute
- Requires approval if requiresApproval is true
- Uses X-Idempotency-Key for idempotent plan creation
Response codes:
- 200 OK
- 400 Bad Request (missing idempotency key)
- 404 Not Found
- 409 Conflict (not approved/closed)

### Transition
POST /deletions/{deletionId}/transition
- Body: TransitionDeletionRequest
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict (invalid transition or proof gating)

### Proof Upload
POST /deletions/{deletionId}/proofs
- multipart/form-data with file
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict

### Close
POST /deletions/{deletionId}/close
- Body: CloseDeletionRequest
- Creates evidence + bundle
 - Returns 503 if evidence service is unavailable; evidence_bundle_id remains null and deletion is not finalized/closed until evidence bundle creation succeeds.
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict (proof or state gating)
- 503 Service Unavailable (evidence down)

## Manual Proof (System Execution)
### Request
POST /deletions/{deletionId}/systems/{executionId}/manual-proof/request
- Body: ManualProofRequest
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict

### Submit
POST /deletions/{deletionId}/systems/{executionId}/manual-proof/submit
- multipart/form-data with file, optional notes
- Returns 503 if evidence service is unavailable
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict
- 503 Service Unavailable (evidence down)

### Decide
POST /deletions/{deletionId}/systems/{executionId}/manual-proof/decide
- Body: ManualProofDecisionRequest (APPROVE|REJECT)
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict

## Exceptions (System Execution)
POST /deletions/{deletionId}/systems/{executionId}/exceptions/grant
- Body: DeletionExceptionGrantRequest
- Returns 503 if evidence service is unavailable
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict
- 503 Service Unavailable (evidence down)

## Tombstones
### Create
POST /tombstones
- Body: TombstoneCreateRequest
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict
- 503 Service Unavailable (evidence down)

### Remove
POST /tombstones/{tombstoneId}/remove
- Body: TombstoneRemoveRequest
Response codes:
- 200 OK
- 404 Not Found
- 409 Conflict
- 503 Service Unavailable (evidence down)

## Error Responses
- 409 Conflict: invalid state transitions (approval/proof gating)
- 503 Service Unavailable: evidence service down for close, manual proof submit, or exception grant
