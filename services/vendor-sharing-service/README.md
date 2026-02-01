# Vendor Sharing Service

DPDP-grade vendor tracking and data sharing compliance service.

## Overview

Manages vendor registry, data processing agreements (DPAs), and tracks data sharing activities with third-party processors. Designed for DPDP Act 2023 compliance with comprehensive cross-border transfer awareness.

## Features

- **Vendor Registry**: Track processors, sub-processors, service providers with risk levels
- **DPA/Agreement Management**: DPAs, MSAs, SCCs, NDAs
- **Sharing Records**: Track data sharing with lawful basis, data categories, frequency
- **Cross-Border Transfer**: DPDP Section 16 compliance with transfer regions
- **Status History**: Append-only audit trail
- **Evidence Export**: Integration with evidence-reporting-service
- **Event Emission**: Audit and outbox events for all mutations

## Database (vendor schema)

- **vendors**: vendor_id, tenant_id, vendor_name, vendor_type, hosting_region, risk_level, enabled
- **vendor_agreements**: agreement_id, vendor_id, agreement_type (DPA/MSA/SCC), status, effective_from/to
- **sharing_records**: sharing_id, vendor_id, activity_id, system_id, lawful_basis, data_categories[], transfer_cross_border, transfer_to_regions[], status
- **sharing_status_history**: history_id, sharing_id, from_status, to_status, changed_at
- **vendor_exports**: export_id, bundle_id, evidence_export_id

## API Endpoints (11)

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

## Business Rules

- **Cross-border validation**: transferCrossBorder=true requires transferToRegions (400)
- **Disabled vendor check**: Cannot share with disabled vendor (409)
- **Date validation**: endAt > startAt (400)
- **Disable sharing**: Sets enabled=false + status=INACTIVE + creates history

## Events

vendor.created, vendor.disabled, vendor.agreement_added, sharing.created, sharing.disabled, sharing.export_created

## Testing

```bash
mvn -pl services/vendor-sharing-service test
```

8 tests (Testcontainers + WireMock)

## Running

```bash
mvn spring-boot:run -pl services/vendor-sharing-service
```

Port: 8090

---

**DPDP Grade**: ✅
