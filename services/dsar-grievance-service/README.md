# DSAR & Grievance Service

**DPDP-grade Data Subject Access Request (DSAR) workflow management** with strict state transitions, SLA enforcement, maker-checker approvals, and evidence bundling.

## Features

- **Full DSAR Lifecycle**: RECEIVED → IN_REVIEW → APPROVED/REJECTED → COMPLETED → CLOSED
- **Request Types**: ACCESS, CORRECT, DELETE, WITHDRAW
- **SLA Enforcement**: 90-day deadline with automatic breach detection
- **Maker-Checker**: Configurable approval workflow for sensitive operations
- **Evidence Bundling**: Automatic evidence bundle creation on closure (integrates with evidence-reporting-service)
- **Idempotency**: Duplicate request prevention via idempotency keys
- **Audit Trail**: Complete audit logging + outbox events for all state transitions
- **Multi-Tenant**: Strict tenant isolation for all operations

## Architecture

### State Machine

```
RECEIVED → IN_REVIEW → NEEDS_INFO → IN_REVIEW
                    ↓
                 APPROVED → COMPLETED → CLOSED
                    ↓
                 REJECTED → CLOSED
```

**Transition Rules:**
- **RECEIVED → IN_REVIEW**: Assignment to reviewer
- **IN_REVIEW → NEEDS_INFO | APPROVED | REJECTED | COMPLETED**: Based on review outcome
- **NEEDS_INFO → IN_REVIEW**: Additional info provided
- **APPROVED/COMPLETED/REJECTED → CLOSED**: Final closure with evidence bundle
- **CLOSED**: Terminal state (no further transitions)

### Database Tables

- **dsar_requests**: Main DSAR entity with status, SLA tracking, approval metadata
- **dsar_status_history**: Append-only audit log of all status transitions
- **outbox_events**: Transactional outbox for reliable event publishing

## API Endpoints

### 1. Create DSAR

```bash
curl -X POST http://localhost:8084/dsar \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "dataPrincipalId": "<data-principal-uuid>",
    "requestType": "ACCESS",
    "details": {
      "requestedData": "all_personal_info",
      "deliveryFormat": "JSON"
    },
    "requiresApproval": false,
    "idempotencyKey": "optional-unique-key"
  }'
```

**Response:**
```json
{
  "dsarId": "uuid",
  "status": "RECEIVED",
  "dueAt": "2024-05-01T10:30:00Z"
}
```

**Business Rules:**
- DELETE requests require approval by default (unless explicitly set to false by TENANT_ADMIN)
- Idempotency: Same tenant + dataPrincipalId + idempotencyKey returns existing DSAR
- SLA: Due date automatically set to +90 days

### 2. Assign DSAR

```bash
curl -X POST http://localhost:8084/dsar/{dsarId}/assign \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "assignedTo": "<reviewer-user-uuid>"
  }'
```

**Response:**
```json
{
  "dsarId": "uuid",
  "assignedTo": "reviewer-uuid",
  "status": "IN_REVIEW"
}
```

**Rules:**
- Only allowed if current status is RECEIVED
- Automatically transitions status to IN_REVIEW
- Records status history entry

### 3. Transition Status

```bash
curl -X POST http://localhost:8084/dsar/{dsarId}/transition \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "toStatus": "COMPLETED",
    "reason": "All requested data compiled and verified"
  }'
```

**Response:**
```json
{
  "dsarId": "uuid",
  "status": "COMPLETED"
}
```

**Rules:**
- Validates transition against state machine (see diagram above)
- Throws IllegalStateException if transition is invalid
- Records status history with reason

### 4. Approve/Reject DSAR

```bash
curl -X POST http://localhost:8084/dsar/{dsarId}/approve \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "decision": "APPROVE",
    "reason": "Deletion request verified as legitimate"
  }'
```

**Response:**
```json
{
  "dsarId": "uuid",
  "approved": true,
  "status": "APPROVED"
}
```

**Rules:**
- Only allowed if requiresApproval=true
- Caller must have DPO/REVIEWER/TENANT_ADMIN role (TODO: RBAC enforcement)
- Sets approvedBy and approvedAt timestamps
- decision must be "APPROVE" or "REJECT"

### 5. Close DSAR

```bash
curl -X POST http://localhost:8084/dsar/{dsarId}/close \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "closureNotes": "Request fulfilled. Data package delivered to data principal.",
    "includeEvidenceIds": ["evidence-uuid-1", "evidence-uuid-2"]
  }'
```

**Response:**
```json
{
  "dsarId": "uuid",
  "status": "CLOSED",
  "evidenceBundleId": "bundle-uuid"
}
```

**Rules:**
- Only allowed if status is COMPLETED, APPROVED, or REJECTED
- **Must create evidence bundle** via evidence-reporting-service
- If evidence service unavailable, returns 503 (DSAR closure requires provable evidence)
- Evidence bundle includes:
  - All evidenceIds from request
  - Auto-generated DSAR summary evidence record
  - Bundle type: DSAR, reference: dsarId
- Sets closedAt timestamp and stores evidenceBundleId

### 6. Get DSAR Details

```bash
curl -X GET http://localhost:8084/dsar/{dsarId} \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>"
```

**Response:**
```json
{
  "dsarId": "uuid",
  "tenantId": "uuid",
  "dataPrincipalId": "uuid",
  "requestType": "ACCESS",
  "status": "COMPLETED",
  "details": {...},
  "requiresApproval": false,
  "assignedTo": "reviewer-uuid",
  "approvedBy": null,
  "approvedAt": null,
  "createdAt": "2024-01-31T10:00:00Z",
  "updatedAt": "2024-02-15T14:30:00Z",
  "dueAt": "2024-05-01T10:00:00Z",
  "closedAt": null,
  "closeEvidenceBundleId": null,
  "closeNotes": null,
  "slaBreached": false
}
```

### 7. Search DSARs

```bash
curl -X GET "http://localhost:8084/dsar?status=IN_REVIEW&page=0&size=20" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>"
```

**Query Parameters:**
- `status` (optional): Filter by status (RECEIVED, IN_REVIEW, COMPLETED, etc.)
- `requestType` (optional): Filter by request type (ACCESS, DELETE, etc.)
- `dataPrincipalId` (optional): Filter by data principal UUID
- `page` (default: 0): Page number
- `size` (default: 20): Page size

**Response:**
```json
{
  "content": [
    { /* DsarDetailResponse */ },
    { /* DsarDetailResponse */ }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

## Events

### Outbox Events

All DSAR operations emit events for downstream processing:

- **dsar.created**: New DSAR received
- **dsar.assigned**: DSAR assigned to reviewer
- **dsar.status_changed**: Status transition occurred
- **dsar.approved**: DSAR approved (maker-checker)
- **dsar.rejected**: DSAR rejected
- **dsar.closed**: DSAR closed with evidence bundle
- **dsar.sla_breached**: SLA deadline breached (90 days)

## SLA Enforcement

**Scheduler**: Runs every 15 minutes (configurable via cron)

```yaml
dsar:
  sla:
    scheduler:
      enabled: true
      cron: "0 */15 * * * *"
```

**Logic:**
- Finds all DSARs where `due_at < now` AND `status != CLOSED` AND `sla_breached = false`
- Marks `sla_breached = true`
- Emits `dsar.sla_breached` event + audit log
- **Does NOT auto-close** (requires manual intervention)

**Disable in tests:**
```yaml
dsar.sla.scheduler.enabled: false
```

## Integration with Other Services

### Evidence Reporting Service

**On Close:**
1. DSAR service calls `POST /bundles` on evidence-reporting-service
2. Creates bundle with:
   - `bundleType: DSAR`
   - `referenceType: DSAR`
   - `referenceId: <dsarId>`
   - `evidenceIds`: All evidence collected for this DSAR
3. Stores returned `bundleId` in `dsar_requests.close_evidence_bundle_id`
4. If evidence service returns error or is unavailable → **503 Service Unavailable** (cannot close without evidence)

**Configuration:**
```yaml
evidence:
  service:
    url: http://localhost:8095
```

### Consent Service (Future Integration)

- When DSAR type is WITHDRAW, emit event for consent-service to revoke consents
- Reference consent records in DSAR details JSON

### Deletion Service (Future Integration)

- When DSAR type is DELETE and status=APPROVED, emit event to trigger retention-deletion-service
- Link deletion job ID in DSAR metadata

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/regulyn
    hikari:
      connection-init-sql: SET search_path TO dsar

dsar:
  sla:
    scheduler:
      enabled: true
      cron: "0 */15 * * * *"

evidence:
  service:
    url: http://localhost:8095
```

## Tech Stack

- Java 21
- Spring Boot 3.3.5
- PostgreSQL 15 (Flyway migrations)
- Testcontainers (integration tests)
- WireMock (evidence service mocking)
- Jackson ObjectMapper (JSON serialization)

## Dependencies

- lib-common (AuditWriter, TenantContext)
- lib-auth (TenantContext)
- lib-events (OutboxWriter, EventFactory)

## Security

- **Tenant Isolation**: All queries filtered by `tenant_id` from TenantContext
- **Idempotency**: Prevents duplicate DSAR creation
- **Audit Trail**: All operations logged to audit_events table
- **Outbox Pattern**: Reliable event delivery via transactional outbox
- **TODO: RBAC**: Approve endpoint should enforce DPO/REVIEWER/TENANT_ADMIN roles

## Testing

```bash
cd services/dsar-grievance-service
mvn clean test
```

**Integration Tests** (Testcontainers + WireMock):
1. Create DSAR → outbox event exists
2. Create with idempotency → returns existing DSAR
3. Assign → status moves to IN_REVIEW + history row
4. Approve flow → only allowed when requiresApproval=true
5. Close → calls evidence service (mocked) and stores bundleId
6. SLA breach scheduler → marks overdue DSARs
7. Invalid transition → throws IllegalStateException
8. Search by status → pagination works

**Test Results**: ✅ 8/8 tests passing

## Future Enhancements

- **RBAC Enforcement**: Role-based access control for approve endpoint
- **Temporal Workflows**: Long-running DSAR fulfillment workflows
- **Email Notifications**: Notify data principals on status changes
- **Automated Data Collection**: Trigger data collection from all services
- **Redaction Engine**: Auto-redact sensitive data in export packages
- **Retention Policies**: Auto-close DSARs after retention period

---

**Last Updated**: 2024-01-31  
**Service Port**: 8084  
**Database Schema**: `dsar`
