# Employee Data Service

**DPDP-grade internal employee data compliance platform** for managing employee rights requests (ACCESS, CORRECT, DELETE, WITHDRAW) with maker-checker approvals, SLA monitoring, and evidence integration.

## Purpose
- **Employee Rights Management**: Handle DPDP employee rights requests with state machine enforcement
- **Maker-Checker Approvals**: Configurable approval gating for sensitive operations
- **SLA Monitoring**: 90-day default SLA with automated breach detection every 15 minutes
- **Evidence Integration**: Automatic bundle creation on request closure
- **Audit Trail**: Append-only status history for all state transitions
- **Data Category Tracking**: Map employee data to processing purposes across systems

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL (employee schema)
- Flyway migrations
- Testcontainers + WireMock

## Dependencies
- lib-common
- lib-auth (TenantContextHolder)
- lib-events (OutboxWriter, EventFactory)
- lib-observability

## Features

### Core Capabilities
- **Employee Directory Stub**: Minimal employee metadata (not a full HRIS)
- **HR Purpose Inventory**: Track lawful bases for data processing (7 purpose types)
- **Data Records**: Track employee data categories across systems (7 categories)
- **Rights Requests**: ACCESS, CORRECT, DELETE, WITHDRAW with 9-status state machine
- **Idempotency**: Deduplication via unique (tenant, employee, idempotencyKey)
- **Multi-tenancy**: Full tenant isolation

## API Endpoints (15 total)

### Employee Directory (2 endpoints)
```bash
POST /api/employees
GET /api/employees?q=&status=
```

### HR Purposes (2 endpoints)
```bash
POST /api/hr-purposes
GET /api/hr-purposes
```

### Data Records (2 endpoints)
```bash
POST /api/employee-data-records
GET /api/employee-data-records?employeeId=&dataCategory=&hrPurposeId=&systemId=
```

### Employee Requests (7 endpoints)
```bash
POST /api/employee-requests
POST /api/employee-requests/{id}/assign
POST /api/employee-requests/{id}/approve  # TENANT_ADMIN/DPO/REVIEWER only
POST /api/employee-requests/{id}/transition
POST /api/employee-requests/{id}/close    # Creates evidence bundle, returns 503 if evidence service down
GET /api/employee-requests/{id}
GET /api/employee-requests?status=&requestType=&employeeId=&page=&size=
```

### Exports (2 endpoints)
```bash
POST /api/exports/employee-compliance
GET /api/employee/exports/{id}/download
```

## State Machine (9 Statuses)

```
RECEIVED → IN_REVIEW → NEEDS_INFO → (back to IN_REVIEW)
              ├→ APPROVED → IN_PROGRESS → COMPLETED → CLOSED
              ├→ REJECTED → CLOSED
              └→ IN_PROGRESS → FAILED → CLOSED
```

**Approval Gating**: If `requiresApproval=true`, request cannot reach IN_PROGRESS until APPROVED.

## Event Types (11 total)

- `employee.created`, `hr_purpose.created`, `employee_record.created`
- `employee_request.created`, `employee_request.assigned`, `employee_request.approved`
- `employee_request.rejected`, `employee_request.status_changed`, `employee_request.closed`
- `employee_request.sla_breached`, `employee_export.created`

All events via OutboxWriter (lib-events).

## Testing

```bash
# Run all tests
mvn -pl services/employee-data-service test

# Build
mvn -pl services/employee-data-service clean install
```

**Test Coverage**: 6 integration tests with Testcontainers (postgres:16-alpine) + WireMock for evidence service.

## Configuration

```yaml
spring:
  flyway:
    schemas: employee
evidence:
  service:
    url: http://localhost:8083
server:
  port: 8091
```

## Database Schema

**Schema**: `employee` (6 tables)

1. **employees**: employee_id, tenant_id, employee_ref, full_name, status
2. **hr_purposes**: hr_purpose_id, tenant_id, purpose_key, lawful_basis
3. **employee_data_records**: record_id, employee_id, data_category, hr_purpose_id
4. **employee_requests**: request_id, employee_id, request_type, status, requires_approval, due_at, sla_breached, idempotency_key, evidence_bundle_id
5. **employee_request_status_history**: history_id, request_id, from_status, to_status, changed_at
6. **employee_exports**: export_id, bundle_id, evidence_export_id

See Flyway migration `V4__employee_domain.sql` for full schema.
