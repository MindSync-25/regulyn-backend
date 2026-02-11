# Children Guardian Service

**DPDP-compliant Age-Gating & Guardian Consent Management Service**

## Overview

The Children Guardian Service implements age-gating and guardian consent workflows for data protection regulations (GDPR Article 8, India DPDP 2023, COPPA). It provides:

- **Age-Gating**: Automatic detection of minors (< 18 years) requiring guardian consent
- **Guardian Verification**: Role-based guardian verification workflow
- **Consent Lifecycle**: Complete state machine for consent submission, approval, revocation, and closure
- **Evidence Integration**: Mandatory evidence bundling for consent closure operations
- **Audit Trail**: Full append-only audit history for all state transitions

## Architecture

### Core Components

1. **Database Schemas** (PostgreSQL)

  **children schema (active domain)**
  - `children.children` - child profiles with date of birth and age-gating
  - `children.guardians` - guardian records with verification status
  - `children.guardian_consents` - consent lifecycle with state machine
  - `children.consent_signed_artifacts` - artifact metadata (no file storage)
  - `children.consent_status_history` - append-only audit trail
  - `children.children_exports` - export tracking for compliance

  **guardian schema (legacy workflow tables still present)**
  - `guardian.child_profiles`
  - `guardian.guardian_verifications`
  - `guardian.guardian_consents`

2. **State Machine**: Consent status transitions
   ```
   SUBMITTED → IN_REVIEW → APPROVED → REVOKED → CLOSED
            ↓             ↓
            REJECTED → CLOSED
   ```

3. **Guardian Eligibility**: Only `VERIFIED` guardians (not `DISABLED`) can create consents

4. **Evidence Integration**: Consent closure requires evidence service availability (returns 503 if down)

### Code Packages

- `com.regulyn.guardian.*` holds the active REST API, services, entities, repositories, and DTOs.
- `com.regulyn.children.*` currently only provides a health check controller.

## API Endpoints

### Child Management (1 endpoint)

**POST /children** - Create child profile with age-gating calculation

### Guardian Management (2 endpoints)

**POST /guardians** - Create guardian record (pending verification)  
**POST /guardians/{guardianId}/verify** - Verify guardian (requires TENANT_ADMIN/DPO/REVIEWER)

### Consent Lifecycle (4 endpoints)

**POST /consents** - Submit guardian consent (guardian must be VERIFIED)  
**POST /consents/{consentId}/approve** - Approve/reject consent (requires TENANT_ADMIN/DPO/REVIEWER)  
**POST /consents/{consentId}/revoke** - Revoke approved consent  
**POST /consents/{consentId}/close** - Close consent with evidence bundle

### Export (2 endpoints)

**POST /exports/guardian-consents** - Create export of guardian consents  
**GET /exports/guardian-consents/{exportId}/download** - Download exported data

## State Machine Rules

| From Status | To Status | Allowed? | Notes |
|-------------|-----------|----------|-------|
| SUBMITTED | IN_REVIEW | ✅ | Optional review step |
| SUBMITTED | APPROVED | ✅ | Direct approval |
| SUBMITTED | REJECTED | ✅ | Direct rejection |
| IN_REVIEW | APPROVED | ✅ | After review |
| IN_REVIEW | REJECTED | ✅ | After review |
| APPROVED | REVOKED | ✅ | User/admin revocation |
| APPROVED | CLOSED | ✅ | Consent closure |
| REJECTED | CLOSED | ✅ | Cleanup |
| REVOKED | CLOSED | ✅ | Final cleanup |
| CLOSED | * | ❌ | Terminal state |

## Guardian Eligibility

- **Can create consent**: `status = VERIFIED` AND `status != DISABLED`
- **Cannot create consent**: `status = PENDING` OR `status = DISABLED`

## Role-Based Access Control

### Guardian Verification
- **Allowed Roles**: `TENANT_ADMIN`, `DPO`, `REVIEWER`
- **Returns**: 403 Forbidden if unauthorized

### Consent Approval/Rejection
- **Allowed Roles**: `TENANT_ADMIN`, `DPO`, `REVIEWER`
- **Returns**: 403 Forbidden if unauthorized

## Event Types (10 total)

1. `child.created` - child profile created with age-gating
2. `guardian.created` - guardian record created
3. `guardian.verified` - guardian verification completed
4. `guardian.disabled` - guardian disabled
5. `consent.submitted` - consent submitted for review
6. `consent.approved` - consent approved
7. `consent.rejected` - consent rejected
8. `consent.revoked` - approved consent revoked
9. `consent.closed` - consent closed with evidence bundle
10. `children.export_created` - export created

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/children_guardian_db
    
evidence:
  service:
    baseUrl: http://localhost:8083
```

## Building

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn clean package
```

## Tech Stack

- Java 21
- Spring Boot 3.x
- PostgreSQL 15+
- Flyway (migrations)
- Hypersistence Utils (JSONB support)

## Dependencies

- lib-common (audit infrastructure)
- lib-auth (tenant context, role-based access)
- lib-events (outbox pattern, event factory)

## Compliance

Implements:
- **GDPR Article 8**: Parental consent for children under 16
- **India DPDP 2023**: Verifiable parental consent for minors
- **COPPA**: Verifiable parental consent for children under 13

Age threshold configurable (default: 18 years).
- lib-observability

---

## Flyway Migrations

- **V1__init.sql**: guardian schema + audit tables
- **V2__create_outbox_table.sql**: outbox table
- **V3__create_guardian_workflow_tables.sql**: legacy guardian workflow tables
- **V4__children_guardian_domain.sql**: children domain tables (active)
