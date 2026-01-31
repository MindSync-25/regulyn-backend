# Consent Service

The Consent Service manages privacy notice templates, versioned content, and consent receipts for GDPR/privacy compliance workflows.

## Purpose

- **Notice Management**: Create, version, and publish privacy notices with multi-language support
- **Consent Tracking**: Record immutable consent receipts linking users to specific notice versions
- **Withdrawal Handling**: Support consent withdrawal with full audit trail
- **Content Integrity**: Hash-based verification of notice content and consent receipts

## Architecture

### Database Schema (consent schema)
- `notice_templates`: Notice definitions by purpose (e.g., "marketing", "analytics")
- `notice_versions`: Versioned notice content with DRAFT/PUBLISHED/RETIRED lifecycle
- `notice_language_text`: Multi-language content with SHA-256 hashes
- `consent_receipts`: Immutable consent records linking to notice versions
- `consent_status_history`: Audit trail of consent status changes

### Business Rules
1. **Single Published Version**: Only one PUBLISHED version per notice at a time; publishing a new version automatically retires the previous one
2. **Immutable Published Content**: Cannot modify language text for PUBLISHED versions; requires creating a new version
3. **Active Notice Requirement**: Consent grants require an active PUBLISHED notice for the purpose
4. **Idempotency**: Duplicate consent grants with same idempotencyKey return existing receipt
5. **Withdrawal**: Only GRANTED consents can be withdrawn; creates history record

## API Endpoints

### Notice Management

#### 1. Create Notice Template
```bash
curl -X POST http://localhost:8082/notices \
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
curl -X POST http://localhost:8082/notices/{noticeId}/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -d '{
    "changeSummary": "Initial version"
  }'

Response: {"versionId": "uuid", "versionNumber": 1}
```

#### 3. Add Language Content
```bash
curl -X POST http://localhost:8082/notices/{noticeId}/versions/{versionId}/languages \
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
curl -X POST http://localhost:8082/notices/{noticeId}/versions/{versionId}/publish \
  -H "X-Tenant-Id: <tenant-uuid>"

Response: {"published": true, "publishedAt": "2026-01-31T..."}
```

#### 5. Get Active Notice
```bash
curl -X GET "http://localhost:8082/notices/active?purpose=marketing&language=en" \
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

### Consent Management

#### 6. Grant Consent
```bash
curl -X POST http://localhost:8082/consents \
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
curl -X POST http://localhost:8082/consents/{receiptId}/withdraw \
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
# All consents for a data principal
curl -X GET "http://localhost:8082/consents?dataPrincipalId=user-uuid" \
  -H "X-Tenant-Id: <tenant-uuid>"

# Filter by purpose
curl -X GET "http://localhost:8082/consents?dataPrincipalId=user-uuid&purpose=marketing" \
  -H "X-Tenant-Id: <tenant-uuid>"

Response: [
  {
    "receiptId": "uuid",
    "dataPrincipalId": "uuid",
    "purpose": "marketing",
    "source": "WIDGET",
    "status": "GRANTED",
    "noticeId": "uuid",
    "versionId": "uuid",
    "versionNumber": 1,
    "language": "en",
    "contentHash": "sha256-hash",
    "receiptHash": "sha256-hash",
    "clientRef": "...",
    "grantedAt": "...",
    "withdrawnAt": null
  }
]
```

## Event Types

All actions emit events to the `outbox_events` table for downstream consumers:

- `notice.created`: New notice template created
- `notice.version_created`: New version created for notice
- `notice.language_added`: Language content added to version
- `notice.published`: Version published (retires previous published version)
- `consent.granted`: Consent granted by data principal
- `consent.withdrawn`: Consent withdrawn

Event payloads include IDs, hashes, and timestamps but **not full content** to keep events lightweight.

## Running Locally

### Prerequisites
- PostgreSQL running (or use Docker Compose)
- Java 21
- Maven

### Start Service
```bash
cd services/consent-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Service runs on port **8082** by default.

### Database
- Schema: `consent`
- Flyway migrations automatically create tables on startup
- Migrations location: `src/main/resources/db/migration`

### Configuration
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/regulyn
    username: regulyn
    password: regulyn
  jpa:
    hibernate:
      ddl-auto: validate  # Strict validation
  flyway:
    enabled: true
    schemas: consent
```

## Evidence Linking (Optional)

The service supports optional evidence recording for compliance auditing:

- **Notice Publishing**: When a version is published, an evidence record can be created
- **Consent Withdrawal**: Withdrawal actions can generate evidence records

Evidence integration is **optional** and gracefully degrades if evidence service is unavailable. Evidence IDs are stored in:
- `notice_versions.evidence_id` (nullable)
- `consent_receipts.withdraw_evidence_id` (nullable)

## Testing

Integration tests use Testcontainers PostgreSQL:

```bash
mvn test
```

Key test scenarios:
- Notice publish flow with version retirement
- Consent grant with idempotency
- Consent withdrawal with history tracking
- Multi-purpose consent listing

## Multi-Tenancy

All operations are tenant-scoped using `X-Tenant-Id` header or JWT claims. Tenant ID is automatically extracted from `TenantContextHolder` and used in all queries.

## Content Integrity

- **Content Hash**: SHA-256 of notice text content
- **Receipt Hash**: SHA-256 of canonical receipt data (tenantId|dataPrincipalId|versionId|contentHash)
- Hashes enable verification of consent authenticity and detect tampering

## Troubleshooting

**"No published version found for purpose"**
- Ensure you've published a version for the purpose: `POST /notices/{id}/versions/{versionId}/publish`

**"Cannot modify published version content"**
- Published versions are immutable; create a new version instead

**"Can only withdraw consents with GRANTED status"**
- Check consent status; already withdrawn consents cannot be withdrawn again

**Idempotency not working**
- Ensure `idempotencyKey` is consistent across retries
- Key is scoped to (tenant, dataPrincipal, purpose, idempotencyKey)

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
