# ROPA Inventory Service

**Record of Processing Activities (ROPA)** - DPDP-compliant processing activity registry and audit-ready export system.

## Overview

The ROPA Inventory Service provides a complete system for managing and documenting data processing activities as required by data protection regulations (GDPR, DPDP, etc.). It maintains:

- **Systems / Data Stores**: Applications, databases, SaaS platforms, and file stores
- **Data Categories**: Types of personal data processed (PII, Financial, Health, etc.)
- **Processing Activities**: Versioned records with lawful basis, purpose, retention, and risk level
- **Linkages**: Connections between activities, systems, data categories, and vendors
- **Audit Exports**: Evidence bundles ready for regulatory review

### Key Features

1. **Activity Versioning**: Immutable published versions; edits create new draft versions
2. **Tenant Isolation**: All operations scoped by tenant_id
3. **Evidence Integration**: Exports to evidence-reporting-service for audit trails
4. **Search & Filter**: Query activities by status, risk level, system, data category
5. **Audit Trail**: All actions logged to audit_events and outbox_events

---

## Database (ropa schema)

### V1__init.sql
- **ropa schema**: created if missing
- **service_meta**: service metadata entry
- **audit_events**: append-only audit table

### V2__create_outbox_table.sql
- **outbox_events**: outbox pattern table (ropa schema)

### V4__ropa_domain.sql

#### ropa_systems
- **Columns**: system_id, tenant_id, system_name (unique per tenant), system_type, owner_team, location, criticality, enabled, metadata (JSONB), created_at, updated_at

#### ropa_data_categories
- **Columns**: data_category_id, tenant_id, category_key (unique per tenant), label, sensitive, metadata (JSONB), created_at

#### ropa_activity_versions
- **Columns**: version_id, tenant_id, activity_id, version_number, status (DRAFT|PUBLISHED|RETIRED), activity_name, purpose, lawful_basis, data_principal_type, description, retention_policy, retention_days, risk_level, enabled, metadata (JSONB), published_at, created_at
- **Uniqueness**: (tenant_id, activity_id, version_number)

#### ropa_activity_links
- **Columns**: link_id, tenant_id, activity_id, version_id, notes, created_at

#### ropa_activity_systems
- **Columns**: id, tenant_id, version_id, system_id
- **Uniqueness**: (tenant_id, version_id, system_id)

#### ropa_activity_data_categories
- **Columns**: id, tenant_id, version_id, data_category_id
- **Uniqueness**: (tenant_id, version_id, data_category_id)

#### ropa_activity_vendors
- **Columns**: id, tenant_id, version_id, vendor_id (reference only, no FK)
- **Uniqueness**: (tenant_id, version_id, vendor_id)

#### ropa_exports
- **Columns**: export_id, tenant_id, bundle_id, evidence_export_id, created_at

## API Endpoints

### Systems / Data Stores

#### Create System
```bash
POST /systems
Content-Type: application/json

{
  "systemName": "CRM Database",
  "systemType": "DATABASE",           # APP|DATABASE|SAAS|FILESTORE|OTHER
  "ownerTeam": "Engineering",         # optional
  "location": "INDIA",                # INDIA|US|EU|OTHER
  "criticality": "HIGH",              # LOW|MED|HIGH
  "metadata": { "vendor": "AWS" }     # optional
}

Response: { "systemId": "<uuid>" }
```

#### List Systems
```bash
GET /systems?type=DATABASE&criticality=HIGH&q=CRM
```

#### Disable System
```bash
POST /systems/{systemId}/disable
```

---

### Data Categories

#### Create Data Category
```bash
POST /data-categories
Content-Type: application/json

{
  "categoryKey": "PII",               # PII|FINANCIAL|HEALTH|BIOMETRIC|CHILD_DATA|EMPLOYEE_DATA|DEVICE|OTHER
  "label": "Personal Identifiable Information",
  "sensitive": true
}

Response: { "dataCategoryId": "<uuid>" }
```

---

### Processing Activities

#### Create Activity (DRAFT)
```bash
POST /activities
Content-Type: application/json

{
  "activityName": "Customer Onboarding",
  "purpose": "Account creation and KYC verification",
  "lawfulBasis": "CONTRACT",          # CONSENT|CONTRACT|LEGAL_OBLIGATION|VITAL_INTERESTS|PUBLIC_TASK|LEGITIMATE_INTERESTS|OTHER
  "dataPrincipalType": "CUSTOMER",    # CUSTOMER|EMPLOYEE|VENDOR|CHILD|OTHER
  "retentionDays": 2555,              # 0-36500
  "riskLevel": "MED"                  # LOW|MED|HIGH
}

Response: { "activityId": "<uuid>", "status": "DRAFT" }
```

#### Link Activity to Systems/Categories
```bash
POST /activities/{activityId}/link
Content-Type: application/json

{
  "systemIds": ["<system-uuid-1>"],
  "dataCategoryIds": ["<category-uuid-1>"],
  "vendorIds": ["<vendor-uuid-1>"],  # optional, references only
  "notes": "Links for onboarding flow"
}

Response: { "activityId": "<uuid>", "linked": true }
```

#### Publish Activity
```bash
POST /activities/{activityId}/publish

Response: { 
  "activityId": "<uuid>", 
  "status": "PUBLISHED", 
  "publishedAt": "2026-01-31T10:00:00Z" 
}
```

**Rules:**
- Published versions are **immutable** for core fields
- Publishing retires any previous PUBLISHED version (status → RETIRED)
- Only DRAFT versions can be edited or linked

#### Create New Version
```bash
POST /activities/{activityId}/versions
Content-Type: application/json

{
  "changeSummary": "Updated retention policy to 5 years"  # optional
}

Response: { "versionId": "<uuid>", "versionNumber": 2 }
```

**Workflow:**
1. Edit activity → create new DRAFT version
2. Link systems/categories to new DRAFT
3. Publish → old PUBLISHED becomes RETIRED, new DRAFT becomes PUBLISHED

---

### ROPA Exports

#### Create Export
```bash
POST /exports/ropa
Content-Type: application/json

{
  "title": "Q1 2026 ROPA Audit",

#### Retention Matrix Export (Evidence Artifact)
```bash
POST /retention/exports/matrix
Content-Type: application/json

{
  "systemIds": ["<system-uuid-1>", "<system-uuid-2>"],
  "activityStatus": "PUBLISHED",
  "includeDisabled": false
}
```

Response (example):
```json
{
  "reportExportId": "<uuid>",
  "status": "CREATED",
  "artifactRef": "evidence/retention/<id>",
  "artifactHash": "<sha256>",
  "rows": [ ... ]
}
```

#### Cross-Border Report Export (Evidence Artifact)
```bash
POST /cross-border/exports/report
Content-Type: application/json

{
  "filters": {
    "vendorId": "<vendor-uuid>",
    "dataCategoryId": "<category-uuid>",
    "sourceRegion": "INDIA",
    "destinationRegion": "US"
  }
}
```

Response (example):
```json
{
  "reportExportId": "<uuid>",
  "status": "CREATED",
  "artifactRef": "evidence/cross-border/<id>",
  "artifactHash": "<sha256>",
  "rowCount": 3,
  "rows": [ ... ]
}
```

#### Evidence Artifacts & Idempotency
- Exports persist an evidence artifact in evidence-reporting-service via `POST /evidence/artifacts`.
- The request payload is hashed (SHA-256) and stored as `payload_hash` in `ropa_report_exports`.
- Replays with the same `X-Idempotency-Key` and matching `payload_hash` return the original export.
- Mismatched payloads return **409 CONFLICT**.

**Audit Events:**
- `RETENTION_MATRIX_EXPORT_REQUESTED` / `RETENTION_MATRIX_EXPORT_CREATED`
- `CROSS_BORDER_REPORT_EXPORT_REQUESTED` / `CROSS_BORDER_REPORT_EXPORT_CREATED`
- `ROPA_EVIDENCE_ARTIFACT_STORED`

**Outbox Events:**
- `ropa.retention_matrix_export_requested` / `ropa.retention_matrix_export_created`
- `ropa.cross_border_report_export_requested` / `ropa.cross_border_report_export_created`
- `ropa.ropa_evidence_artifact_stored`
  "periodFrom": "2026-01-01",
  "periodTo": "2026-03-31",
  "filters": {
    "status": "PUBLISHED",
    "riskLevel": "HIGH"
  }
}

Response: {
  "bundleId": "<uuid>",
  "exportId": "<uuid>",
  "downloadPath": "/ropa/exports/<exportId>/download"
}
```

#### Download Export
```bash
GET /ropa/exports/{exportId}/download

Response: binary data from evidence-reporting-service
```

---

## Event Types

### Audit Events
- `SYSTEM_CREATED`, `SYSTEM_DISABLED`
- `DATA_CATEGORY_CREATED`
- `ACTIVITY_CREATED`, `ACTIVITY_LINKED`, `ACTIVITY_VERSION_CREATED`, `ACTIVITY_PUBLISHED`
- `ROPA_EXPORT_CREATED`

### Outbox Events
- `ropa.system_created`, `ropa.system_disabled`
- `ropa.data_category_created`
- `ropa.activity_created`, `ropa.activity_linked`, `ropa.activity_version_created`, `ropa.activity_published`
- `ropa.export_created`

---

## Testing

```bash
mvn -pl services/ropa-inventory-service test
```

**Test Coverage:**
- 7 integration tests with Testcontainers (PostgreSQL)
- WireMock for evidence service mocking
- Tests cover: systems, categories, activities, versioning, linking, publishing, exports

---

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL (ropa schema)
- Flyway migrations
- Testcontainers + WireMock

## Dependencies
- lib-common
- lib-events
- lib-observability
