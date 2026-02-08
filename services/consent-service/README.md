# Consent Service

The Consent Service manages privacy notice templates, versioned content, and consent receipts for GDPR/privacy compliance workflows. It includes a legacy consent-ingestion API and a full notice/consent workflow (v2).

## Scope & Purpose (Round 1)
- **Legacy consent capture**: A minimal consent API that stores hashed notice text + receipt hash
- **Notice management**: Create, version, and publish privacy notices with multi-language support
- **Consent tracking**: Record immutable consent receipts linked to notice versions
- **Withdrawal handling**: Support consent withdrawal with full audit trail
- **Content integrity**: Hash-based verification of notice content and consent receipts
- **Audit + Outbox**: All critical actions emit audit + outbox events

## Architecture

### Database Schema (schema: `consent`)
- `consent_records` (legacy consent capture)
- `notice_templates`
- `notice_versions`
- `notice_language_text`
- `consent_receipts`
- `consent_status_history`
- `purpose_versions` (Round 2 Part 1)
- `purpose_version_history` (Round 2 Part 1)
- `communication_consent_ledger` (Round 2 Part 1)
- `consent_invalidations` (Round 2 Part 1)
- `reconsent_requirements` (Round 2 Part 1)
- `outbox_events`
- `audit_events`

### Business Rules
1. **Single Published Version**: Only one PUBLISHED version per notice at a time; publishing a new version retires the previous one
2. **Immutable Published Content**: Cannot modify language text for PUBLISHED versions; create a new version instead
3. **Active Notice Requirement**: Consent grants require an active PUBLISHED notice for the purpose
4. **Idempotency**: Duplicate grants with same `idempotencyKey` return existing receipt
5. **Withdrawal**: Only GRANTED consents can be withdrawn; creates history record

## API Surfaces

### Legacy Consent API (Round 1, minimal)
Base path: `/consents`

#### Create Consent Record
```bash
curl -X POST http://localhost:8082/consents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "userId": "user-uuid",
    "purpose": "marketing",
    "language": "en",
    "noticeText": "full notice text",
    "source": "WIDGET"
  }'

Response: {"receiptId": "uuid", "payloadHash": "sha256-hash"}
```

Notes:
- Stores hashed notice text (no raw notice in outbox).
- Emits `consent.created` outbox event.

### Notice + Consent Workflow API (v2)
Base path: `/api/v2/consent`

#### 1. Create Notice Template
```bash
curl -X POST http://localhost:8082/api/v2/consent/notices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "purpose": "marketing",
    "title": "Marketing Communications",
    "category": "promotional",
    "defaultLanguage": "en"
  }'

Response: {"noticeId": "uuid"}
```

#### 2. Create Version
```bash
curl -X POST http://localhost:8082/api/v2/consent/notices/{noticeId}/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "changeSummary": "Initial version"
  }'

Response: {"versionId": "uuid", "versionNumber": 1}
```

#### 3. Add Language Content
```bash
curl -X POST http://localhost:8082/api/v2/consent/notices/{noticeId}/versions/{versionId}/languages \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "language": "en",
    "content": "We would like to send you marketing emails about our products and services."
  }'

Response: {"languageId": "uuid", "contentHash": "sha256-hash"}
```

#### 4. Publish Version
```bash
curl -X POST http://localhost:8082/api/v2/consent/notices/{noticeId}/versions/{versionId}/publish \
  -H "X-Tenant-Id: <tenant-uuid>"

Response: {"published": true, "publishedAt": "2026-01-31T..."}
```

#### 5. Get Active Notice
```bash
curl -X GET "http://localhost:8082/api/v2/consent/notices/active?purpose=marketing&language=en" \
  -H "X-Tenant-Id: <tenant-uuid>"

Response: {
  "noticeId": "uuid",
  "versionId": "uuid",
  "versionNumber": 1,
  "purpose": "marketing",
  "language": "en",
  "content": "full notice text",
  "contentHash": "sha256-hash",
  "publishedAt": "..."
}
```

#### 6. Grant Consent
```bash
curl -X POST http://localhost:8082/api/v2/consent/consents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "dataPrincipalId": "user-uuid",
    "purpose": "marketing",
    "language": "en",
    "source": "WIDGET",
    "clientRef": "optional-ref",
    "idempotencyKey": "unique-key-123"
  }'

Response: {
  "receiptId": "uuid",
  "status": "GRANTED",
  "receiptHash": "sha256-hash",
  "noticeVersionId": "uuid",
  "contentHash": "sha256-hash"
}
```

#### 7. Withdraw Consent
```bash
curl -X POST http://localhost:8082/api/v2/consent/consents/{receiptId}/withdraw \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "reason": "User requested opt-out"
  }'

Response: {
  "receiptId": "uuid",
  "status": "WITHDRAWN",
  "withdrawnAt": "2026-01-31T..."
}
```

#### 8. List Consents
```bash
curl -X GET "http://localhost:8082/api/v2/consent/consents?dataPrincipalId=user-uuid" \
  -H "X-Tenant-Id: <tenant-uuid>"

curl -X GET "http://localhost:8082/api/v2/consent/consents?dataPrincipalId=user-uuid&purpose=marketing" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

## Event Types (Outbox)
- `consent.created` (legacy consent capture)
- `notice.created`
- `notice.version_created`
- `notice.language_added`
- `notice.published`
- `consent.granted`
- `consent.withdrawn`

Event payloads include IDs, hashes, and timestamps but not full notice content.

## Evidence Linking (Schema Fields)
- `notice_versions.evidence_id` (nullable)
- `consent_receipts.withdraw_evidence_id` (nullable)
- `consent_receipts.evidence_artifact_id` (Round 2 Part 1, nullable)

No evidence service client is wired yet; fields are reserved for Round 2.

## Configuration
```yaml
spring:
  application:
    name: consent-service
  datasource:
    url: ${spring.datasource.url:jdbc:postgresql://localhost:5432/consent}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    schemas: consent

server:
  port: 8082
```

## Round 2 Part 1 (Persistence Only)
Added schema structures and entity mappings for:
- Purpose versioning (`purpose_versions`, `purpose_version_history`)
- Communication consent ledger (`communication_consent_ledger`)
- Consent invalidations (`consent_invalidations`)
- Reconsent requirements (`reconsent_requirements`)
- New nullable linkage fields on `consent_receipts`:
  - `purpose_version_id`
  - `language_code`
  - `notice_language_text_id`
  - `notice_content_hash_sha256`
  - `evidence_artifact_id`

No Round 2 business logic or APIs are added yet.

## Multi-Tenancy
- Tenant scoped via `X-Tenant-Id` (or JWT claims)
- Tenant ID + Actor ID are sourced from `TenantContextHolder`

## Content Integrity
- **Content Hash**: SHA-256 of notice text content
- **Receipt Hash**: SHA-256 of canonical receipt data (tenantId|dataPrincipalId|versionId|contentHash)
- **Payload Hash**: SHA-256 used in legacy `consent_records`

## Testing
- `ConsentServiceOutboxTest`
- `ConsentWorkflowIntegrationTest` (Testcontainers PostgreSQL)
- `ConsentSchemaRound2Part1MigrationIT` (Testcontainers schema validation)

## Round 2 Final Closure Checks (Confirmed)
1) **E2E fail-closed**
  - Widening → validity false until re-grant
  - Translation unavailable → `/active-dual` fails, grant fails, no rows inserted
2) **DB writes asserted in E2E** (audit + outbox via SQL against Testcontainers Postgres)
3) **No raw content in audit/outbox** (payload text does not include notice content)
4) **Idempotency verified**
  - Grant `idempotencyKey` returns same receipt
  - Comm ledger dedupe returns same ledger row and no duplicate events
5) **Backward compatibility**
  - Round-1 legacy consent + v2 basic flow tests green
6) **Docs are specific**
  - Region→language mapping, fail-closed rules, event list, test commands, deployment checklist

## Running Locally
```bash
cd services/consent-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Troubleshooting
**"No published version found for purpose"**
- Publish a version: `POST /api/v2/consent/notices/{id}/versions/{versionId}/publish`

**"Cannot modify published version content"**
- Create a new version instead

**"Can only withdraw consents with GRANTED status"**
- Withdraw only GRANTED receipts

**Idempotency not working**
- Ensure `idempotencyKey` matches for retries

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL
- Kafka (events)
- Temporal (workflows)

## Dependencies
- lib-common
- lib-events
- lib-temporal
- lib-observability
