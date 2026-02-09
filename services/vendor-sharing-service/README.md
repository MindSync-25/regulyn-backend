# Vendor Sharing Service

DPDP-grade vendor tracking and data sharing compliance service.

## Overview

Manages vendor registry, data processing agreements (DPAs), and tracks data sharing activities with third-party processors. Designed for DPDP Act 2023 compliance with comprehensive cross-border transfer awareness.

## Features

- **Vendor Registry**: Track processors, sub-processors, service providers with risk levels
- **DPA/Agreement Management**: DPAs, MSAs, SCCs, NDAs
- **Sharing Records**: Track data sharing with lawful basis, data categories, frequency
- **Cross-Border Transfer**: DPDP Section 16 compliance with transfer regions
- **Status History**: Append-only status audit trail
- **Evidence Export**: Integration with evidence-reporting-service
- **Event Emission**: Outbox events for mutations (vendor.* and sharing.*)

## Database (vendor schema)

### V1__init.sql
- **vendor schema**: created if missing
- **service_meta**: service metadata entry
- **audit_events**: audit table (written by Round 2 telemetry ingestion + export workflows)

### V2__create_outbox_table.sql
- **outbox_events**: outbox pattern table (vendor schema)

### V4__vendor_sharing_domain.sql

#### vendors
- **Columns**: vendor_id, tenant_id, vendor_name (unique per tenant), vendor_type, contact_email, country, hosting_region, enabled, risk_level, metadata (JSONB), created_at, updated_at

#### vendor_agreements
- **Columns**: agreement_id, tenant_id, vendor_id, agreement_type, status, effective_from, effective_to, doc_ref, notes, created_at

#### sharing_records
- **Columns**: sharing_id, tenant_id, vendor_id, activity_id, system_id, sharing_purpose, lawful_basis, data_categories[], frequency, transfer_cross_border, transfer_to_regions[], transfer_notes, start_at, end_at, enabled, metadata (JSONB), status, created_at, updated_at

#### sharing_status_history
- **Columns**: history_id, tenant_id, sharing_id, from_status, to_status, changed_at, changed_by, reason

#### vendor_exports
- **Columns**: export_id, tenant_id, bundle_id, evidence_export_id, created_at

### V5__vendor_access_telemetry_tables.sql

#### vendor_access_events
- **Columns**: access_event_id, tenant_id, vendor_id, system_name, source, access_type, subject_ref, data_categories[], purpose_ref, purpose_version, accessed_at, correlation_id, actor_type, actor_id, ip, user_agent, result, raw_payload_hash, raw_payload_ref (JSONB), received_at
- **Idempotency**: unique (tenant_id, vendor_id, correlation_id, accessed_at, access_type)

#### vendor_access_exports
- **Columns**: export_id, tenant_id, vendor_id, requested_by_user_id, requested_at, range_start, range_end, format, status, idempotency_key, artifact_ref, file_hash, total_events, allowed_events, denied_events, error_events, notes
- **Idempotency**: unique index on (tenant_id, idempotency_key) where idempotency_key is not null

## API Endpoints (16)

1. POST /vendor/vendors - Create vendor
2. GET /vendor/vendors - List (filter: enabled, riskLevel, q)
3. POST /vendor/vendors/{id}/disable
4. POST /vendor/vendors/{id}/agreements - Create DPA
5. GET /vendor/vendors/{id}/agreements
6. POST /vendor/sharing-records - Create sharing
7. POST /vendor/sharing-records/{id}/disable
8. GET /vendor/sharing-records - List (paginated, filters)
9. GET /vendor/sharing-records/{id} - Get detail
10. POST /vendor/exports/vendor-sharing - Create export
11. GET /vendor/exports/{id}/download
12. POST /vendor/access-events/ingest - Batch ingest vendor access telemetry
13. GET /vendor/access-events - Query access events (vendorId or subjectRef required)
14. GET /vendor/access-events/vendors/{vendorId}/summary - Aggregate access event counts
15. POST /vendor/access-exports - Request vendor access export (requires X-Idempotency-Key)
16. POST /vendor/internal/access-exports/{exportId}/process - Process export (internal only; requires validated X-Internal-Auth + X-Tenant-ID; intended for ops/tests)

## Business Rules

- **Cross-border validation**: transferCrossBorder=true requires transferToRegions (400)
- **Disabled vendor check**: Cannot share with disabled vendor (409)
- **Date validation**: endAt > startAt (400)
- **Disable sharing**: Sets enabled=false + status=INACTIVE + creates history
- **Access event query guardrails**: requires vendorId or subjectRef, max range 90 days, size <= 200
- **Access event query response**: rawPayloadRef is not returned by default (use includeRawRef=true to include)
- **Access export guardrails**: requires X-Idempotency-Key, vendorId, max range 90 days, to > from
- **Access export status**: REQUESTED -> PROCESSING -> CREATED | FAILED
- **Evidence artifacts**: exports stored as VENDOR_ACCESS_LOG_EXPORT
- **Export contents**: includes access metadata + rawPayloadHash; excludes rawPayloadRef (even when includeRawRef=true for API views)
- **Export size guidance**: request narrow ranges; large ranges may be rejected or processed slowly depending on volume

## Events (Outbox)

vendor.created, vendor.disabled, vendor.agreement_added, sharing.created, sharing.disabled, sharing.export_created,
vendor_access.event_ingested, vendor_access.event_duplicate_ignored,
vendor_access.export_requested, vendor_access.export_created, vendor_access.evidence_artifact_stored, vendor_access.export_failed

## Audit Events (audit_events.event_type)

VENDOR_ACCESS_EVENT_INGESTED, VENDOR_ACCESS_EVENT_DUPLICATE_IGNORED,
VENDOR_ACCESS_EXPORT_REQUESTED, VENDOR_ACCESS_EXPORT_CREATED,
VENDOR_ACCESS_EVIDENCE_ARTIFACT_STORED, VENDOR_ACCESS_EXPORT_FAILED

## Round 2 Final Hardening Checklist

- Idempotency constraints present for access events and access exports
- Ingest writes audit/outbox for ingested/duplicate
- Queries are tenant-scoped with pagination caps and range guards
- Access export requests write audit/outbox
- Export processor stores evidence artifact and writes audit/outbox
- Tests: mvn -pl services/vendor-sharing-service test

## Testing

```bash
mvn -pl services/vendor-sharing-service test
```

Testcontainers + WireMock tests:
- VendorServiceTest
- VendorExportServiceTest
- VendorAccessTelemetrySchemaTest
- VendorAccessTelemetryIngestTest
- VendorAccessTelemetryQueryTest
- VendorAccessTelemetryExportTest

## Running

```bash
mvn spring-boot:run -pl services/vendor-sharing-service
```

Port: 8090

---

**DPDP Grade**: ✅
