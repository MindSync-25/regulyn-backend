# Scanner Service

## Overview
Scanner Service is a data discovery and inventory service that automatically scans various data sources to identify entities, fields, and retention candidates. It provides pluggable scan adapters, integrates with retention-deletion-service for candidate promotion, and supports evidence-backed exports.

## Purpose
- **Automated Data Discovery**: Scan data sources (databases, APIs) to discover entities and fields
- **Risk Assessment**: Classify discovered data by risk level (LOW, MEDIUM, HIGH)
- **Retention Candidate Identification**: Identify data eligible for retention/deletion policies
- **Promotion to Retention Service**: Push retention candidates to retention-deletion-service
- **Evidence-Backed Exports**: Create audit exports with evidence service integration
- **Multi-Tenant Support**: Full tenant isolation with X-Tenant-ID header

## Key Features
- ✅ **Pluggable Scan Adapters**: MOCK (deterministic testing), HTTP_DISCOVERY (REST-based scanning)
- ✅ **Scan Source Management**: Register, list, disable data sources
- ✅ **Scan Execution**: INVENTORY (entities/fields), RETENTION_CANDIDATES, or BOTH modes
- ✅ **Finding Persistence**: Store discovered entities/fields with metadata
- ✅ **Result Hashing**: SHA-256 hash for change detection across runs
- ✅ **Retention Promotion**: Idempotent promotion with configurable risk thresholds
- ✅ **Scan Exports**: Time-range exports with evidence integration
- ✅ **Event Emission**: All lifecycle events via OutboxWriter (scanner.source_created, scanner.run_succeeded, etc.)

## Architecture

### Scan Adapters
Scanner service uses pluggable adapters to support different data source types:

#### MOCK Adapter
- **Purpose**: Deterministic testing with fixed findings
- **Use Case**: Integration tests, demos
- **Behavior**:
  - Inventory: 4 findings (2 entities: customer_profile/orders, 2 fields: email-PII/payment_method-FINANCIAL)
  - Retention Candidates: 5 findings with fixed UUIDs (00000000-0000-0000-0000-000000000001 through 000000000005)
  - Risk Levels: HIGH, MEDIUM, MEDIUM, LOW, MEDIUM (stable across runs)

#### HTTP_DISCOVERY Adapter
- **Purpose**: REST-based data source scanning
- **Use Case**: Production scanning of services with discovery endpoints
- **Behavior**:
  - Calls GET {baseUrl}/discover?since=<lastRunTimestamp>
  - Supports authentication: API_KEY (X-API-Key header), BEARER (Authorization: Bearer token)
  - Parses JSON response with entities/fields/retentionCandidates arrays
  - Throws exception on HTTP errors

### Scan Execution Flow
1. **Create Scan Source** → POST /scanner/sources (ACTIVE status, specify adapter type)
2. **Create Scan Run** → POST /scanner/runs (QUEUED status, select scan mode)
3. **Execute Run** → POST /scanner/runs/{id}/execute
   - Updates status to RUNNING
   - Selects adapter based on source_type
   - Executes inventory and/or retention candidates scan
   - Persists findings to scan_findings table
   - Computes SHA-256 result_hash (canonical representation: findingType:entityType:riskLevel sorted)
   - Updates status to SUCCEEDED/FAILED
   - Emits events: scanner.run_started, scanner.findings_created, scanner.run_succeeded/failed
4. **Get Findings** → GET /scanner/runs/{id}/findings
5. **Promote to Retention** → POST /scanner/runs/{id}/promote/retention-candidates
   - Filters by minRiskLevel (LOW includes all, MED includes MED+HIGH, HIGH only HIGH)
   - Calls retention-deletion-service POST /retention/candidates for each subject
   - Tracks promoted_count/failed_count
   - Prevents double-promotion with idempotency check (existsByTenantIdAndRunId)
6. **Export Scans** → POST /scanner/exports/scans (creates evidence-backed export)
   - Fetches all runs in time range
   - Calls evidence-service workflow: createEvidence → createBundle → createExport
   - Returns export with download link
7. **Download Export** → GET /scanner/exports/{id}/download

### Database Schema (V4__scanner_domain.sql)

#### scan_sources
- **Purpose**: Register data sources to scan
- **Columns**: id, tenant_id, source_name (unique per tenant), source_type (MOCK/HTTP_DISCOVERY), status (ACTIVE/DISABLED), connection_string, auth_type (NONE/API_KEY/BEARER), auth_ref, metadata (JSONB), created_at, updated_at
- **Indexes**: (tenant_id, status), (tenant_id, source_type)

#### scan_runs
- **Purpose**: Track scan execution
- **Columns**: id, tenant_id, source_id, scan_mode (INVENTORY/RETENTION_CANDIDATES/BOTH), status (QUEUED/RUNNING/SUCCEEDED/FAILED), finding_count, result_hash (SHA-256), error_message, queued_at, started_at, completed_at
- **Indexes**: (tenant_id, source_id, queued_at), (tenant_id, status)

#### scan_findings
- **Purpose**: Store discovered entities/fields/candidates
- **Columns**: id, tenant_id, run_id, finding_type (ENTITY/FIELD/RETENTION_CANDIDATE), entity_type, field_name, subject_id, subject_type (USER/TRANSACTION/etc), risk_level (LOW/MEDIUM/HIGH), description, metadata (JSONB), created_at
- **Indexes**: (tenant_id, run_id), (tenant_id, finding_type, risk_level)

#### scan_promotions
- **Purpose**: Track retention candidate promotions
- **Columns**: id, tenant_id, run_id (unique per tenant for idempotency), promoted_count, failed_count, promoted_at
- **Indexes**: (tenant_id, run_id)

#### scanner_exports
- **Purpose**: Track scan exports
- **Columns**: id, tenant_id, evidence_export_id, start_time, end_time, export_format, run_count, created_at
- **Indexes**: (tenant_id, created_at)

### Event Types
- **scanner.source_created**: New scan source registered
- **scanner.run_created**: Scan run created (QUEUED)
- **scanner.run_started**: Scan run execution started (RUNNING)
- **scanner.run_succeeded**: Scan run completed successfully
- **scanner.run_failed**: Scan run failed with error
- **scanner.findings_created**: Findings persisted to database
- **scanner.retention_promoted**: Retention candidates promoted to retention-deletion-service
- **scanner.export_created**: Scan export created

## API Endpoints

### Scan Sources

#### POST /scanner/sources
Create new scan source
- **Headers**: X-Tenant-ID
- **Body**: CreateScanSourceRequest
  ```json
  {
    "sourceName": "Production Database",
    "sourceType": "HTTP_DISCOVERY",
    "status": "ACTIVE",
    "connectionString": "https://api.example.com",
    "authType": "API_KEY",
    "authRef": "PROD_API_KEY_ENV"
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
- **Error**: Cannot disable source with running scans

### Scan Runs

#### POST /scanner/runs
Create new scan run
- **Headers**: X-Tenant-ID
- **Body**: CreateScanRunRequest
  ```json
  {
    "sourceId": "uuid",
    "scanMode": "BOTH"
  }
  ```
- **Response**: ScanRunResponse (201 Created)
- **Validation**: Source must be ACTIVE
- **Error**: 409 Conflict if source is DISABLED

#### POST /scanner/runs/{id}/execute
Execute queued scan run
- **Headers**: X-Tenant-ID
- **Response**: ScanRunResponse (200 OK)
- **Error**: 409 Conflict if run is not QUEUED
- **Side Effects**:
  - Updates status to RUNNING → SUCCEEDED/FAILED
  - Persists findings to scan_findings
  - Computes result_hash (SHA-256)
  - Emits events

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
    "minRiskLevel": "MEDIUM",
    "limit": 100
  }
  ```
- **Response**: PromoteRetentionCandidatesResponse (200 OK)
  ```json
  {
    "promotedCount": 42,
    "failedCount": 3
  }
  ```
- **Error**: 409 Conflict if run already promoted (idempotency check)
- **Behavior**:
  - Filters findings by finding_type=RETENTION_CANDIDATE and risk_level >= minRiskLevel
  - Calls retention-deletion-service POST /retention/candidates for each subject
  - Continues on individual failures (non-blocking)
  - Prevents duplicate subjects in same promotion

### Exports

#### POST /scanner/exports/scans
Create scan export with evidence integration
- **Headers**: X-Tenant-ID
- **Body**: CreateScanExportRequest
  ```json
  {
    "startTime": "2024-01-01T00:00:00Z",
    "endTime": "2024-01-31T23:59:59Z",
    "exportFormat": "JSON"
  }
  ```
- **Response**: ScanExportResponse (201 Created)
- **Evidence Workflow**:
  1. createEvidence(type="SCAN_REPORT_SNAPSHOT")
  2. createBundle(type="AUDIT_EXPORT")
  3. createExport()

#### GET /scanner/exports/{id}/download
Download scan export (proxies to evidence-service)
- **Headers**: X-Tenant-ID
- **Response**: application/octet-stream (200 OK)

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

### Service Dependencies
- **retention-deletion-service**: http://localhost:8086 (POST /retention/candidates)
- **evidence-reporting-service**: http://localhost:8083 (evidence workflow)

## Testing

### Integration Tests
Scanner service includes 9 comprehensive integration tests with Testcontainers PostgreSQL and WireMock:

1. **testCreateSourceAndList**: Create ACTIVE source, verify in list
2. **testDisableSourcePreventsNewRuns**: Disable source, verify 409 Conflict on new run creation
3. **testExecuteRunWithMockAdapter**: Execute MOCK adapter, verify 9 findings (4 inventory + 5 retention candidates)
4. **testExecuteRunIdempotency**: Execute run twice, verify 409 Conflict on second execution
5. **testHttpDiscoveryAdapterWithWireMock**: WireMock stub GET /discover, verify findings from HTTP response
6. **testPromoteRetentionCandidates**: WireMock stub retention service, verify promotedCount/failedCount
7. **testPromotionNoDoublePromotion**: Promote run, try again, verify 409 Conflict
8. **testExportScansWithWireMock**: WireMock stubs for evidence workflow, verify export creation and download
9. **testOutboxEventsCreated**: Verify full flow executes successfully (events mocked)

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
3. Add `YOUR_ADAPTER_NAME` to `SourceType` enum
4. Adapter will be auto-discovered by `ScanRunService` via Map injection

### Idempotency Guarantees
- **Scan Execution**: Cannot execute same run twice (status must be QUEUED)
- **Retention Promotion**: Cannot promote same run twice (existsByTenantIdAndRunId check in scan_promotions)

### Result Hash Computation
SHA-256 hash of canonical representation for change detection:
```
findingType1:entityType1:riskLevel1
findingType2:entityType2:riskLevel2
...
```
(sorted alphabetically for consistency)

## Security
All endpoints require authentication with `ADMIN` or `SCANNER_AGENT` role via Spring Security `@PreAuthorize`.

## Multi-Tenancy
All operations are scoped by `X-Tenant-ID` header. Database queries include `tenant_id` filters to ensure data isolation.
