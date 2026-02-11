# Nominee Service — Round 2 Status

## Context
- Service: nominee-service
- Stack: Spring Boot 3.3.x, Java 21, Postgres (schema: nominee), Flyway

## Flyway Migrations
- V1__init.sql
- V2__create_outbox_table.sql
- V3__create_nominee_workflow_tables.sql
- V4__nominee_domain_enhancement.sql
- Next: V5

## Round‑1 Tables (Existing)
- nominees
- nominee_claims
- claim_documents
- claim_status_history
- rights_grants
- nominee_exports

## Round 2 — Part 1 Scope
### Why a new table is required
A new table `nominee_documents` is required so nominee verification documents can exist independently of claims (e.g., upload during verification before a claim is created). The ledger must be immutable and evidence‑backed (artifact ref + sha256) without storing binaries in DB.

### Optional bridge (backward‑compatible)
`claim_documents` can optionally reference `nominee_documents` via a nullable `nominee_document_id` column. This preserves all existing Round‑1 behavior while enabling linkage in later parts.

### Optional requirements config
`nominee_verification_requirements` provides tenant‑scoped verification step requirements for future workflow gating.

## Part 1 Deliverables
- V5 migration for `nominee_documents`, optional `nominee_verification_requirements`, and optional `claim_documents` bridge column.
- JPA entities + repositories for new tables.
- Schema integration test validating tables, columns, constraints, and indexes.
