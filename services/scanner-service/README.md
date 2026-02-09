# Scanner Service

## Overview
Scanner Service is a data discovery and inventory service that automatically scans data sources to identify entities, fields, and retention candidates. It provides pluggable scan adapters, integrates with retention-deletion-service for candidate promotion, and supports evidence-backed exports.

## Purpose
- **Automated Data Discovery**: Scan data sources (databases, APIs) to discover entities and fields
- **Risk Assessment**: Classify discovered data by risk level (LOW, MED, HIGH)
- **Retention Candidate Identification**: Identify data eligible for retention/deletion policies
- **Promotion to Retention Service**: Push retention candidates to retention-deletion-service
- **Evidence-Backed Exports**: Create audit exports with evidence service integration
- **Multi-Tenant Support**: Full tenant isolation with X-Tenant-ID header

## Key Features
- ✅ **Pluggable Scan Adapters**: MOCK (deterministic testing), HTTP_DISCOVERY (REST-based scanning)
- ✅ **Website Scanner**: WEBSITE adapter with crawl summaries, fingerprints, and PARTIAL runs
- ✅ **Scan Source Management**: Register, list, disable data sources (requires `systemId`)
- ✅ **Scan Execution**: INVENTORY, RETENTION_CANDIDATES, or BOTH modes with `since` filter
- ✅ **Finding Persistence**: Store discovered entities/fields/candidates with metadata
- ✅ **Result Hashing**: SHA-256 hash for change detection across runs
- ✅ **Retention Promotion**: Idempotent promotion per run (scan_promotions)
- ✅ **Scan Exports**: Time-range exports with evidence integration
- ✅ **Remediation Tasks**: Generate tasks from findings with lifecycle transitions
- ✅ **Evidence Artifacts + Bundles**: Task close/waive proof + run evidence bundles
- ✅ **Event Emission**: Outbox events for lifecycle actions (scanner.*)

## Round 2 Documentation
Detailed design and behavior notes:
- docs/scanner/ROUND2_PART1_persistence.md
- docs/scanner/ROUND2_PART2_website_scanner.md
- docs/scanner/ROUND2_PART3_tasks.md
- docs/scanner/ROUND2_PART4_evidence.md
- docs/scanner/ROUND2_FINAL_CHECKLIST.md

## Architecture

### Scan Adapters
Scanner service uses pluggable adapters to support different data source types:

#### MOCK Adapter
- **Purpose**: Deterministic testing with fixed findings
- **Use Case**: Integration tests, demos
- **Behavior**:
  - Inventory: 4 findings (2 entities: customer_profile/orders, 2 fields: email-PII/payment_method-FINANCIAL)
  - Retention Candidates: 5 findings with fixed UUIDs (00000000-0000-0000-0000-000000000001 through 000000000005)
  - Risk Levels (retention): HIGH, MED, MED, LOW, MED
  - Confidence scores are deterministic (see adapter for exact values)

#### HTTP_DISCOVERY Adapter
- **Purpose**: REST-based data source scanning
- **Use Case**: Production scanning of services with discovery endpoints
- **Behavior**:
  - Calls GET `{baseUrl}/discover?since=<lastRunTimestamp>` when `since` is provided
  - Supports authentication: API_KEY (`X-API-Key` header), BEARER (`Authorization: Bearer token`)
  - Parses JSON response with `entities`, `fields`, `retentionCandidates`
  - Throws exception on HTTP errors

### Scan Execution Flow
1. **Create Scan Source** → POST /scanner/sources (ACTIVE status, specify adapter type)
2. **Create Scan Run** → POST /scanner/runs (QUEUED status, select scan mode, optional `since` and `requestRef`)
3. **Execute Run** → POST /scanner/runs/{id}/execute
   - Updates status to RUNNING
   - Selects adapter based on `source_type`
   - Executes inventory and/or retention candidates scan
   - Persists findings to `scan_findings`
   - Computes SHA-256 `result_hash` (canonical representation: `findingType:entityType:riskLevel` sorted and joined by `|`)
   - Updates status to SUCCEEDED/FAILED
   - Emits events: `scanner.run_started`, `scanner.findings_created`, `scanner.run_succeeded`/`scanner.run_failed`
4. **Get Findings** → GET /scanner/runs/{id}/findings
5. **Promote to Retention** → POST /scanner/runs/{id}/promote/retention-candidates
   - Filters by `minRiskLevel` (LOW → LOW+MED+HIGH, MED → MED+HIGH, HIGH → HIGH)
   - Requires `subjectType` and optional `entityType`
   - Calls retention-deletion-service POST /retention/candidates for each subject
   - Tracks promoted_count/failed_count
   - Prevents double-promotion per run using `scan_promotions`
6. **Export Scans** → POST /scanner/exports/scans (creates evidence-backed export)
   - Fetches runs and filters by `periodFrom`/`periodTo`
   - Calls evidence-service workflow: createEvidence → createBundle → createExport
   - Returns export with download link
7. **Download Export** → GET /scanner/exports/{id}/download

## Database Schema (Flyway)

### V1__init.sql
- **scanner schema**: created if missing
- **audit_events** (public schema): shared audit table used by `AuditWriter`
- **service_meta**: service metadata entry

### V2__create_outbox_table.sql
- **outbox_events** (public schema): outbox pattern table used by `OutboxWriter`

### V4__scanner_domain.sql

#### scan_sources
- **Purpose**: Register data sources to scan
- **Columns**: source_id, tenant_id, source_name (unique per tenant), system_id, source_type (MOCK/HTTP_DISCOVERY), status (ACTIVE/DISABLED), base_url, auth_type (NONE/API_KEY/BEARER), auth_ref, metadata (JSONB), created_at, updated_at
- **Indexes**: (tenant_id, status), (tenant_id, source_type)

#### scan_runs
- **Purpose**: Track scan execution
- **Columns**: run_id, tenant_id, source_id, scan_mode (INVENTORY/RETENTION_CANDIDATES/BOTH), status (QUEUED/RUNNING/SUCCEEDED/FAILED), since_at, request_ref, queued_at, started_at, finished_at, findings_count, result_hash, error_message
- **Indexes**: (tenant_id, status, queued_at), (tenant_id, source_id, queued_at)

#### scan_findings
- **Purpose**: Store discovered entities/fields/candidates
- **Columns**: finding_id, tenant_id, run_id, finding_type (ENTITY/FIELD/RETENTION_CANDIDATE), entity_type, subject_id, field_name, data_category, risk_level (LOW/MED/HIGH), confidence, details (JSONB), created_at
- **Indexes**: (tenant_id, run_id), (tenant_id, entity_type), (tenant_id, subject_id)

#### scan_promotions
- **Purpose**: Track retention candidate promotions
- **Columns**: promotion_id, tenant_id, run_id, promoted_at, promoted_count, failed_count, details (JSONB)
- **Indexes**: (tenant_id, run_id, promoted_at)

#### scanner_exports
- **Purpose**: Track scan exports
- **Columns**: export_id, tenant_id, bundle_id, evidence_export_id, created_at
- **Indexes**: (tenant_id, created_at)

## Event Types (Outbox + Audit actions)
- **scanner.source_created**: New scan source registered
- **scanner.source_disabled**: Scan source disabled
- **scanner.run_created**: Scan run created (QUEUED)
- **scanner.run_started**: Scan run execution started (RUNNING)
- **scanner.run_succeeded**: Scan run completed successfully
- **scanner.run_partial**: Scan run completed partially
- **scanner.run_failed**: Scan run failed with error
- **scanner.findings_created**: Findings persisted to database
- **scanner.finding_detected**: Findings detected (summary)
- **scanner.retention_promoted**: Retention candidates promoted to retention-deletion-service
- **scanner.export_created**: Scan export created
- **scanner.task_created_from_finding**: Tasks generated from findings
- **scanner.task_status_changed**: Task status transition
- **scanner.task_event_added**: Task note/attachment event added
- **scanner.task_evidence_artifact_stored**: Task close/waive evidence stored
- **scanner.scan_evidence_bundle_created**: Run evidence bundle created

## API Endpoints

### Health Check

#### GET /health-check
Returns basic service status.

### Scan Sources

#### POST /scanner/sources
Create new scan source
- **Headers**: X-Tenant-ID
- **Body**: CreateScanSourceRequest
  ```json
  {
    "sourceName": "Production Database",
    "systemId": "uuid",
    "sourceType": "HTTP_DISCOVERY",
    "status": "ACTIVE",
    "baseUrl": "https://api.example.com",
    "authType": "API_KEY",
    "authRef": "PROD_API_KEY_ENV",
    "metadata": {"region": "us-east-1"}
  }
  ```
- **Response**: ScanSourceResponse (201 Created)
- **Security**: @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")

#### GET /scanner/sources
List scan sources with optional filters
- **Headers**: X-Tenant-ID
- **Query Params**: status (ACTIVE/DISABLED), type (MOCK/HTTP_DISCOVERY)
- **Response**: List<ScanSourceResponse> (200 OK)

#### POST /scanner/sources/{id}/disable
Disable scan source (prevents new runs)
- **Headers**: X-Tenant-ID
- **Response**: ScanSourceResponse (200 OK)
- **Security**: @PreAuthorize("hasRole('ADMIN')")

### Scan Runs

#### POST /scanner/runs
Create new scan run
- **Headers**: X-Tenant-ID
- **Body**: CreateScanRunRequest
  ```json
  {
    "sourceId": "uuid",
    "scanMode": "BOTH",
    "since": "2024-01-01T00:00:00Z",
    "requestRef": "external-request-id"
  }
  ```
- **Response**: ScanRunResponse (201 Created)
- **Validation**: Source must be ACTIVE

#### POST /scanner/runs/{id}/execute
Execute queued scan run
- **Headers**: X-Tenant-ID
- **Response**: ScanRunResponse (200 OK)
- **Error**: 409 Conflict if run is not QUEUED

#### GET /scanner/runs
List scan runs with pagination
- **Headers**: X-Tenant-ID
- **Query Params**: status, sourceId, page (default 0), size (default 20)
- **Response**: Page<ScanRunResponse> (200 OK)

#### GET /scanner/runs/{id}
Get scan run by ID
- **Headers**: X-Tenant-ID
- **Response**: ScanRunResponse (200 OK)

### Findings

#### GET /scanner/runs/{id}/findings
Get findings for scan run
- **Headers**: X-Tenant-ID
- **Response**: List<FindingResponse> (200 OK)

### Retention Promotion

#### POST /scanner/runs/{id}/promote/retention-candidates
Promote retention candidates to retention-deletion-service
- **Headers**: X-Tenant-ID
- **Body**: PromoteRetentionCandidatesRequest
  ```json
  {
    "minRiskLevel": "MED",
    "subjectType": "CUSTOMER",
    "entityType": "customer_profile",
    "limit": 100
  }
  ```
- **Response**: PromoteRetentionCandidatesResponse (200 OK)
  ```json
  {
    "runId": "uuid",
    "promotedCount": 42,
    "failedCount": 3
  }
  ```
- **Error**: 409 Conflict if run already promoted (idempotency check)

### Exports

#### POST /scanner/exports/scans
Create scan export with evidence integration
- **Headers**: X-Tenant-ID
- **Body**: CreateScanExportRequest
  ```json
  {
    "periodFrom": "2024-01-01T00:00:00Z",
    "periodTo": "2024-01-31T23:59:59Z",
    "filters": {"sourceType": "HTTP_DISCOVERY"}
  }
  ```
- **Response**: ScanExportResponse (201 Created)
  ```json
  {
    "bundleId": "uuid",
    "exportId": "uuid",
    "downloadPath": "/scanner/exports/{exportId}/download"
  }
  ```
- **Evidence Workflow**:
  1. createEvidence(type="SCAN_REPORT_SNAPSHOT")
  2. createBundle(type="AUDIT_EXPORT")
  3. createExport()

#### GET /scanner/exports/{id}/download
Download scan export (proxies to evidence-service)
- **Headers**: X-Tenant-ID
- **Response**: application/octet-stream (200 OK)

### Remediation Tasks

#### POST /scanner/runs/{id}/tasks/generate
Generate tasks from run findings
- **Headers**: X-Tenant-ID

#### GET /scanner/tasks
List tasks
- **Headers**: X-Tenant-ID
- **Query Params**: status, sourceId, runId, severity, page, size

#### GET /scanner/tasks/{id}
Get task by ID
- **Headers**: X-Tenant-ID

#### POST /scanner/tasks/{id}/transition
Transition task status (OPEN → IN_PROGRESS/CLOSED/WAIVED)
- **Headers**: X-Tenant-ID, X-User-ID

#### POST /scanner/tasks/{id}/events
Add task note/attachment event
- **Headers**: X-Tenant-ID, X-User-ID

### Evidence Bundle

#### POST /scanner/runs/{id}/evidence/bundle
Create run evidence bundle (idempotent)
- **Headers**: X-Tenant-ID, X-User-ID

## Configuration

### application.yml
```yaml
spring:
  application:
    name: scanner-service
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: 8094
```

### application-local.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/regulyn
    username: regulyn
    password: regulyn_dev_password
  jpa:
    properties:
      hibernate:
        default_schema: scanner
  flyway:
    schemas: scanner
    locations: classpath:db/migration
```

### Service Dependencies
- **retention-deletion-service**: http://localhost:8086 (POST /retention/candidates)
- **evidence-reporting-service**: http://localhost:8083 (evidence workflow)

## Testing

### Integration Tests
Scanner service includes baseline and Round-2 integration tests (Testcontainers PostgreSQL + WireMock):

**Round-1 baseline (ScannerServiceIntegrationTest)**
1. **testCreateSourceAndList**: Create ACTIVE source, verify in list
2. **testDisableSourcePreventsNewRuns**: Disable source, verify new run creation fails
3. **testExecuteRunWithMockAdapter**: Execute MOCK adapter, verify 9 findings (4 inventory + 5 retention candidates)
4. **testExecuteRunIdempotency**: Execute run twice, verify 409 Conflict on second execution
5. **testHttpDiscoveryAdapterWithWireMock**: WireMock stub GET /discover, verify findings from HTTP response
6. **testPromoteRetentionCandidates**: WireMock stub retention service, verify promotedCount/failedCount
7. **testPromotionNoDoublePromotion**: Promote run, try again, verify 409 Conflict
8. **testExportScansWithWireMock**: WireMock stubs for evidence workflow, verify export creation and download
9. **testFullWorkflowSucceeds**: Full happy path without assertions on outbox content

**Round-2**
- **ScannerRound2PersistenceSchemaIT**: Schema + partial unique index checks
- **WebsiteScannerIT**: WEBSITE crawler persistence + PARTIAL handling
- **RemediationTasksIT**: Task generation + transitions + audit/outbox
- **TaskEvidenceIT**: Task close evidence artifact + idempotency
- **RunEvidenceBundleIT**: Run evidence bundle creation + idempotency

### Run Tests
```bash
cd services/scanner-service
mvn clean test
```

### Build Service
```bash
mvn clean install
```

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL
- Kafka (events)

## Dependencies
- **lib-common**: Common utilities and security
- **lib-events**: OutboxWriter, EventFactory
- **lib-auth**: Authentication/authorization
- **hypersistence-utils-hibernate-63**: JSONB support (3.8.2)
- **spring-boot-starter-security**: Security annotations
- **spring-boot-starter-validation**: Jakarta validation
- **testcontainers**: PostgreSQL testing
- **wiremock-standalone**: HTTP mocking (3.3.1)

## Development Notes

### Adding New Scan Adapters
1. Implement `ScanAdapter` interface
2. Annotate with `@Component("YOUR_ADAPTER_NAME")`
3. Add `YOUR_ADAPTER_NAME` to `CreateScanSourceRequest.SourceType` enum
4. Adapter is auto-discovered by `ScanRunService` via Map injection

### Idempotency Guarantees
- **Scan Execution**: Cannot execute same run twice (status must be QUEUED)
- **Retention Promotion**: Cannot promote same run twice (existsByTenantIdAndRunId check in scan_promotions)

### Result Hash Computation
SHA-256 hash of canonical representation for change detection:
```
findingType1:entityType1:riskLevel1|findingType2:entityType2:riskLevel2|...
```
(sorted alphabetically for consistency)

## Security
All endpoints require authentication with `ADMIN` or `SCANNER_AGENT` role via Spring Security `@PreAuthorize`.

## Multi-Tenancy
All operations are scoped by `X-Tenant-ID` header. Database queries include `tenant_id` filters to ensure data isolation.
