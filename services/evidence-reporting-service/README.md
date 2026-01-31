# Evidence Reporting Service - Bundle Management

This service provides **legal-grade evidence bundling** with cryptographic integrity verification for GDPR compliance workflows (DSAR, deletion, incident management, and audit trails).

## Features

- **Immutable Evidence Bundles**: Tamper-proof containers grouping evidence records and artifacts
- **Cryptographic Integrity**: SHA-256 hashing for bundles, items, and exports
- **Export to ZIP**: Complete evidence packages with manifest + evidence + artifacts + checksums
- **Integrity Verification**: Recompute and validate all hashes to detect tampering
- **Audit Trail**: Full audit logging + outbox events for all bundle operations
- **Multi-Tenant**: Strict tenant isolation for all operations

## Architecture

### Database Tables

- **evidence_bundles**: Immutable bundle metadata with canonical manifest JSON + bundle hash
- **evidence_bundle_items**: Evidence records and artifacts within bundles
- **evidence_exports**: ZIP export packages with file paths + export hashes

### Hashing Strategy

- **Bundle Hash**: SHA-256 of canonical (alphabetically sorted) manifest JSON
- **Item Hash**: SHA-256 of evidence record payload or artifact content
- **Export Hash**: SHA-256 of complete ZIP file

## API Endpoints

### 1. Create Evidence Bundle

```bash
curl -X POST http://localhost:8095/bundles \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  -d '{
    "bundleType": "DSAR",
    "referenceType": "DSAR",
    "referenceId": "dsar-12345",
    "title": "DSAR Request - John Doe",
    "description": "Evidence bundle for DSAR request #12345",
    "evidenceIds": ["evidence-uuid-1", "evidence-uuid-2"],
    "artifactIds": ["artifact-uuid-1"],
    "metadata": {
      "requestDate": "2024-01-15",
      "requesterEmail": "john.doe@example.com"
    }
  }'
```

**Response:**
```json
{
  "bundleId": "bundle-uuid",
  "bundleHash": "abc123...def789",
  "status": "CREATED"
}
```

### 2. Get Bundle Manifest

```bash
curl -X GET http://localhost:8095/bundles/{bundleId} \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>"
```

**Response:**
```json
{
  "bundleId": "bundle-uuid",
  "bundleType": "DSAR",
  "referenceType": "DSAR",
  "referenceId": "dsar-12345",
  "title": "DSAR Request - John Doe",
  "description": "Evidence bundle for DSAR request #12345",
  "bundleHash": "abc123...def789",
  "status": "CREATED",
  "createdAt": "2024-01-15T10:30:00Z",
  "createdBy": "user-uuid",
  "items": [
    {
      "itemId": "item-uuid-1",
      "itemType": "EVIDENCE",
      "evidenceId": "evidence-uuid-1",
      "itemHash": "evidence-hash-1",
      "itemMeta": {"evidenceType": "SCREENSHOT"}
    },
    {
      "itemId": "item-uuid-2",
      "itemType": "ARTIFACT",
      "artifactId": "artifact-uuid-1",
      "itemHash": "artifact-hash-1"
    }
  ],
  "metadata": {
    "requestDate": "2024-01-15",
    "requesterEmail": "john.doe@example.com"
  }
}
```

### 3. Export Bundle to ZIP

```bash
curl -X POST http://localhost:8095/bundles/{bundleId}/export \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>"
```

**Response:**
```json
{
  "exportId": "export-uuid",
  "status": "READY",
  "downloadPath": "./data/evidence-exports/<tenant-id>/<export-id>.zip",
  "exportHash": "xyz789...abc123"
}
```

### 4. Download Export

```bash
curl -X GET http://localhost:8095/exports/{exportId}/download \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>" \
  --output evidence-bundle.zip
```

Returns: `application/octet-stream` (ZIP file)

### 5. Verify Bundle Integrity

```bash
curl -X POST http://localhost:8095/bundles/{bundleId}/verify \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -H "X-User-ID: <user-uuid>"
```

**Response (Valid):**
```json
{
  "bundleId": "bundle-uuid",
  "valid": true,
  "problems": []
}
```

**Response (Tampered):**
```json
{
  "bundleId": "bundle-uuid",
  "valid": false,
  "problems": [
    "Bundle hash mismatch: expected abc123, got def456",
    "Evidence hash mismatch for evidence-uuid-2"
  ]
}
```

## Export Package Structure

```
evidence-bundle-{exportId}.zip
├── manifest.json          # Bundle metadata + canonical manifest
├── evidence/
│   ├── {evidenceId-1}.json
│   ├── {evidenceId-2}.json
│   └── ...
├── artifacts/             # (future: when artifact storage implemented)
│   ├── {artifactId-1}/
│   │   └── document.pdf
│   └── ...
└── checksums.txt          # SHA-256 hashes for all files
```

**manifest.json Example:**
```json
{
  "version": "1.0",
  "bundleType": "DSAR",
  "referenceType": "DSAR",
  "referenceId": "dsar-12345",
  "tenantId": "tenant-uuid",
  "evidenceIds": ["evidence-uuid-1", "evidence-uuid-2"],
  "artifactIds": ["artifact-uuid-1"],
  "metadata": {
    "requestDate": "2024-01-15",
    "requesterEmail": "john.doe@example.com"
  },
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**checksums.txt Example:**
```
# Evidence Bundle Export Checksums
bundle_id: bundle-uuid
bundle_hash: abc123...def789

evidence/evidence-uuid-1.json: hash1...
evidence/evidence-uuid-2.json: hash2...
artifacts/artifact-uuid-1/document.pdf: hash3...
```

## Bundle Types

- **DSAR**: Data Subject Access Request evidence
- **DELETION**: Right to deletion compliance evidence
- **INCIDENT**: Data breach investigation evidence
- **NOMINEE**: Guardian/nominee consent evidence
- **GUARDIAN**: Children guardian consent evidence
- **AUDIT_EXPORT**: Periodic compliance audit exports

## Reference Types

- **DSAR**: Reference to DSAR request ID
- **DELETION**: Reference to deletion request ID
- **INCIDENT**: Reference to incident ticket ID
- **NOMINEE**: Reference to nominee record ID
- **GUARDIAN**: Reference to guardian record ID
- **PERIOD**: Reference to time period (e.g., "2024-01")

## Events

### Outbox Events

All bundle operations emit events for downstream processing:

- **evidence.bundle_created**: New bundle created
- **evidence.bundle_exported**: Bundle exported to ZIP
- **evidence.bundle_verified**: Integrity verification passed
- **evidence.export_failed**: Export failed (with failure reason)
- **evidence.bundle_verify_failed**: Verification failed (tampering detected)

## Integration with Other Services

### DSAR Workflow

1. **dsar-grievance-service** receives DSAR request
2. Collects all user evidence (consents, data access logs, etc.)
3. Creates evidence bundle: `POST /bundles` with `bundleType=DSAR`
4. Exports bundle: `POST /bundles/{id}/export`
5. Delivers ZIP to data subject: `GET /exports/{id}/download`
6. Verifies bundle before delivery: `POST /bundles/{id}/verify`

### Deletion Workflow

1. **retention-deletion-service** schedules deletion
2. Creates evidence bundle with deletion proof
3. Stores bundle hash in deletion audit trail
4. Exports for compliance record

### Incident Management

1. **incident-breach-service** detects breach
2. Collects forensic evidence (logs, access records)
3. Creates incident bundle with all evidence
4. Exports for legal/regulatory reporting
5. Verifies integrity before submission

## Configuration

```yaml
evidence:
  storage:
    localDir: ./data/evidence          # Evidence artifacts storage
  export:
    baseDir: ./data/evidence-exports   # ZIP export storage
```

## Tech Stack
- Java 21
- Spring Boot 3.3.5
- PostgreSQL 15 (Flyway migrations)
- Testcontainers
- SHA-256 cryptographic hashing
- Jackson ObjectMapper (canonical JSON)

## Dependencies
- lib-common (AuditWriter, TenantContext)
- lib-auth (TenantContext)
- lib-events (OutboxWriter, EventFactory)
- lib-evidence (EvidenceRecord, ArtifactStore)

## Security

- **Tenant Isolation**: All queries filtered by tenant_id from TenantContext
- **Immutability**: Bundles are append-only (no updates allowed)
- **Integrity**: SHA-256 hashing prevents tampering
- **Audit**: All operations logged to audit_events table
- **Outbox**: All events reliably published via transactional outbox

## Testing

```bash
cd services/evidence-reporting-service
mvn clean test
```

**Integration Tests** (Testcontainers PostgreSQL):
- Bundle creation with outbox events
- Export to ZIP with manifest + evidence
- Integrity verification (valid + tampered scenarios)

**Test Results**: ✅ 5/5 tests passing (BUILD SUCCESS)

## Future Enhancements

- **S3 Object Lock**: Replace local filesystem with S3 + Object Lock for immutability
- **Artifact Storage**: Implement artifact bundling when artifact storage is available
- **Digital Signatures**: Add cryptographic signatures for non-repudiation
- **Encryption**: Encrypt export ZIPs with recipient public keys
- **Batch Exports**: Support exporting multiple bundles in one package
- **Retention**: Automatic archival/deletion of old bundles

---

**Last Updated**: 2024-01-31  
**Service Port**: 8095  
**Database Schema**: `evidence`