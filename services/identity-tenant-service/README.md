# Identity & Tenant Service

Identity management and multi-tenant configuration service for the Regulyn platform. This service owns tenant/user/role data, issues JWTs, and validates API keys for internal services.

## Current Capabilities (as-is)
- **Login + JWT issuance** via `POST /auth/login`
- **Tenant/user/roles introspection** via `GET /auth/me` (reads `TenantContextHolder`)
- **Tenant-scoped user creation** via `POST /users` (requires `TENANT_ADMIN` role)
- **User invite flow** (create + accept) with idempotency and evidence/audit/outbox
- **User lock/unlock** via admin endpoints
- **API key lifecycle** (create/rotate/revoke) with idempotency and evidence/audit/outbox
- **Plan limits + usage tracking** with read-only enforcement
- **Feature flags** (per-tenant key/value + enabled)
- **Admin audit views** (filters + pagination)
- **Internal API key validation** via `POST /internal/api-keys/validate` (protected by `X-Internal-Auth` header)
- **Internal usage increment** via `POST /internal/tenants/{tenantId}/usage/increment` (idempotent)
- **Health check** via `GET /identity/ping`

## API Surface

### Auth
**POST /auth/login**
- Request: email + password
- Behavior: validates user, checks `enabled`, loads roles, returns JWT + user metadata

**GET /auth/me**
- Returns `tenantId`, `userId`, `roles` from `TenantContextHolder`

### Users
**POST /users**
- Requires `TENANT_ADMIN`
- Creates a tenant-scoped user with hashed password

**POST /users/{userId}/lock**
- Requires `TENANT_ADMIN`
- Locks user (evidence + audit/outbox)

**POST /users/{userId}/unlock**
- Requires `TENANT_ADMIN`
- Unlocks user (evidence + audit/outbox)

**POST /users/invites**
- Requires `TENANT_ADMIN`
- Creates a user invite (idempotent, evidence + audit/outbox)

**POST /users/invites/accept**
- Accepts an invite token (single-use; expires; generic error responses to reduce token enumeration)

### Internal (service-to-service)
**POST /internal/api-keys/validate**
- Validates API key (HMAC-SHA256 for new keys; SHA-256 only for legacy rows) + revoked/expiry/enabled checks
- Returns `CONNECTOR_AGENT` role on success
- Protected by `X-Internal-Auth` header (`InternalAuthFilter`)

**POST /internal/tenants/{tenantId}/usage/increment**
- Idempotent usage increment for DSAR/export counters
- Protected by `X-Internal-Auth` header (`InternalAuthFilter`)

### API Keys
**POST /api-keys**
- Requires `TENANT_ADMIN`
- Create API key (idempotent, evidence + audit/outbox)

**POST /api-keys/{apiKeyId}/rotate**
- Requires `TENANT_ADMIN`
- Rotate API key (idempotent, evidence + audit/outbox)

**POST /api-keys/{apiKeyId}/revoke**
- Requires `TENANT_ADMIN`
- Revoke API key (evidence + audit/outbox)

### Tenants
**GET /tenants/plan-limits**
- Requires `TENANT_ADMIN`
- Returns current limits + usage counters

**PUT /tenants/plan-limits**
- Requires `TENANT_ADMIN`
- Upserts limits (evidence + audit/outbox)

**GET /tenants/feature-flags**
- Requires `TENANT_ADMIN`
- Returns tenant feature flags

**PUT /tenants/feature-flags/{flagKey}**
- Requires `TENANT_ADMIN`
- Upserts feature flag (evidence + audit/outbox)

### Admin
**GET /admin/audit-events**
- Requires `TENANT_ADMIN`
- Filter by `userId`, `eventType`, time range, pagination

### Health
**GET /identity/ping**
- Returns service status

## Tenant Lifecycle (Round 2)

Tenant status is stored in `tenants.status` and enforced in login + write operations.

### Status values
- `DRAFT` → created tenant, not yet activated
- `ACTIVE` → fully operational
- `SUSPENDED` → blocks login and protected write operations
- `DELETED` → terminal state (guarded delete); blocks login and protected write operations

### Lifecycle endpoints
These endpoints perform evidence → audit → outbox emission and enforce a strict state machine with pessimistic locking:

- `POST /tenants`  
	Create tenant (DRAFT). Emits `TENANT_CREATED_DRAFT`.

- `POST /tenants/{tenantId}/bootstrap-admin`  
	One-time admin bootstrap. Emits `TENANT_ADMIN_BOOTSTRAPPED`.

- `POST /tenants/{tenantId}/activate`  
	DRAFT → ACTIVE. Emits `TENANT_ACTIVATED`.

- `POST /tenants/{tenantId}/suspend`  
	ACTIVE → SUSPENDED. Emits `TENANT_SUSPENDED`.

- `POST /tenants/{tenantId}/resume`  
	SUSPENDED → ACTIVE. Emits `TENANT_RESUMED`.

- `POST /tenants/{tenantId}/delete-request`  
	Sets `delete_requested_at`. Emits `TENANT_DELETE_REQUESTED`.

- `DELETE /tenants/{tenantId}`  
	Guarded delete:
	- requires `delete_requested_at` present
	- blocks if `compliance_hold=true` → emits `TENANT_DELETE_BLOCKED`
	- otherwise sets status=DELETED + `deleted_at` → emits `TENANT_DELETED`

Evidence is created *before* DB writes (fail-safe 503 if evidence is unavailable).

## Read-Only Enforcement (Plan Limits)

For `ACTIVE` tenants, identity-tenant-service can flip `tenants.read_only=true` when plan limits are exceeded.
This is enforced in identity write operations (fail-fast, no silent changes).

### Blocked operations when read-only=true
- `POST /users`
- `POST /users/invites`
- `POST /api-keys`
- `POST /api-keys/{apiKeyId}/rotate`

### Allowed operations when read-only=true
(Security/admin/config operations remain available)
- `POST /users/{userId}/lock`
- `POST /users/{userId}/unlock`
- `POST /api-keys/{apiKeyId}/revoke`
- `PUT /tenants/plan-limits`
- `PUT /tenants/feature-flags/{flagKey}`

Read-only toggles emit:
- `TENANT_READ_ONLY_ENABLED`
- `TENANT_READ_ONLY_DISABLED`

Plan limits updates emit:
- `TENANT_PLAN_LIMITS_UPDATED`

## Audit + Outbox Events (Round 2)

This service writes `audit_events` (append-only) and `outbox_events` for critical actions.
Payloads never include secrets (no JWTs, raw API keys, invite tokens, or passwords). Payload hashing uses canonicalized JSON.

### Tenant lifecycle
- `TENANT_CREATED_DRAFT`
- `TENANT_ADMIN_BOOTSTRAPPED`
- `TENANT_ACTIVATED`
- `TENANT_SUSPENDED`
- `TENANT_RESUMED`
- `TENANT_DELETE_REQUESTED`
- `TENANT_DELETE_BLOCKED`
- `TENANT_DELETED`

### Tenant configuration
- `TENANT_PLAN_LIMITS_UPDATED`
- `TENANT_FEATURE_FLAG_UPDATED`
- `TENANT_READ_ONLY_ENABLED`
- `TENANT_READ_ONLY_DISABLED`

### Users
- `USER_INVITED`
- `INVITE_ACCEPTED`
- `USER_LOCKED`
- `USER_UNLOCKED`

### API keys
- `API_KEY_CREATED`
- `API_KEY_ROTATED`
- `API_KEY_REVOKED`
- `API_KEY_USED` (optional; emitted on successful internal validation if enabled)

## Idempotency

Idempotency is implemented via `identity.idempotency_keys`:
- stores request hash + encrypted response data (AES-GCM with AAD binding)
- TTL enforced via `expires_at` and indexed for cleanup

### Supported idempotent operations
- Invite creation: `POST /users/invites` (via `X-Idempotency-Key`)
- API key create/rotate: `POST /api-keys`, `POST /api-keys/{apiKeyId}/rotate` (via `X-Idempotency-Key`)
- Internal usage increment: `POST /internal/tenants/{tenantId}/usage/increment` (requires `X-Idempotency-Key`)

Rules:
- same tenant+scope+key + same request body → returns stored response
- same key with different body → 409 conflict
- expired idempotency rows behave as miss (client must use a new idempotency key)

## Security & Context
- **JWT** issued by `JwtTokenService` (lib-auth). Secret + expiry configured in `application.yml`.
- **Password hashing** uses Spring `PasswordEncoder`.
- **Internal auth**: `InternalAuthFilter` enforces `X-Internal-Auth` for `/internal/**`.
- **Tenant context**: `TenantContextHolder` is read by `/auth/me` and used in `UserService` for tenant-scoped creates.
- **API keys** are **stored as HMAC-SHA256 hashes** in `api_keys` and never stored in plaintext.
- **Invite tokens** are hashed (HMAC-SHA256) and never stored in plaintext.
- **Idempotency** persists encrypted response tokens (AES-GCM) with AAD binding.

## Data Model (Tables)
- `tenants`
- `users`
- `roles`
- `user_roles`
- `api_keys`
- `audit_events` (append-only)
- `outbox_events` (event relay)
- `service_meta` (service metadata)
- `idempotency_keys`
- `tenant_feature_flags`
- `tenant_plan_limits`
- `tenant_monthly_usage`
- `user_invites`

## Flyway Migrations (names + purpose)
1. **V1__create_tenants_table.sql**
	- Creates `identity` schema
	- Creates `tenants`, `service_meta`, `audit_events`
2. **V2__create_users_table.sql**
	- Creates `users` with tenant scoping and indexes
3. **V3__create_roles_table.sql**
	- Creates `roles` (tenant-scoped) and constraints
4. **V4__create_user_roles_table.sql**
	- Creates `user_roles` join table (many-to-many)
5. **V5__create_api_keys_table.sql**
	- Creates `api_keys` with SHA-256 hash column + indexes
6. **V6__seed_local_data.sql**
	- Seeds local tenant, admin user, roles, and a connector API key (local dev only)
7. **V7__create_outbox_table.sql**
	- Creates `outbox_events` for reliable event publishing
8. **V8__round2_identity_part1_persistence.sql**
	- Round 2 Part 1 persistence only (tenant lifecycle fields + status check update)
	- Adds tenant feature flags, plan limits, monthly usage
	- Adds user invites (hashed tokens only)
	- Adds API key metadata columns (no renames)
9. **V9__round2_identity_part3_idempotency.sql**
	- Idempotency table and support for encrypted responses
10. **V10__round2_identity_part3_idempotency_ttl.sql**
	- Idempotency TTL handling
11. **V11__round2_identity_part4_user_lock_and_api_key_ops.sql**
	- User lock fields and API key lifecycle fields
12. **V12__round2_identity_part5_audit_indexes.sql**
	- Audit query performance indexes

## Local Seed Data (from V6)
- Tenant: `11111111-1111-1111-1111-111111111111`
- Admin user: `admin@local.test` / password `admin123`
- Roles: `TENANT_ADMIN`, `DPO`, `CONNECTOR_AGENT`
- API key: `local-connector-key-12345`

## Configuration
### application.yml
- `server.port=8081`
- `jwt.secret`, `jwt.expiration-ms`

### application-local.yml
- PostgreSQL connection to `identity` schema
- Flyway enabled with `classpath:db/migration`
- `internal.auth.token` for `/internal/**` endpoints

## Testing
- `InternalControllerTest` (validates internal auth + API key validation behavior)
- `IdentitySchemaFlywayIT` (Testcontainers + Flyway schema assertions for Round 2 Part 1)
- `TenantLifecycleIT` (tenant lifecycle transitions + evidence/audit/outbox)
- `UserInviteIT` (invite flow + idempotency + evidence/audit/outbox)
- `UserLockAndApiKeyIT` (lock/unlock + API key lifecycle)
- `TenantReadOnlyAndAuditViewIT` (plan limits, read-only enforcement, usage idempotency, audit view, feature flags)

Run schema IT explicitly:
- `mvn -Dtest=IdentitySchemaFlywayIT test`

## Round 2 Part 1 Summary (Persistence Only)
- Added V8 migration with tenant lifecycle fields and safe status constraint update.
- Added new tables: `tenant_feature_flags`, `tenant_plan_limits`, `tenant_monthly_usage`, `user_invites`.
- Added API key metadata columns (`key_version`, `revoked_at`, `rotated_from_api_key_id`, `prefix`, `hash_alg`).
- Extended entities + repositories for new tables (no business logic, no endpoints).
- Added schema integration tests that assert constraints and defaults.

## Round 2 Part 1 Checklist (Verified)
- Tenants status CHECK is only `DRAFT/ACTIVE/SUSPENDED/DELETED`.
- FK targets reference `users(user_id)`.
- API keys columns added without renaming existing columns.
- Invite partial unique index enforces one active invite per tenant+email.
- Defaults verified (tenant flags, plan limits, monthly usage counters).
- Constraint enforcement verified (invalid status + duplicates fail).

## Running Locally
- Profile: `local`
- Port: `8081`

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL

## Dependencies
- lib-common
- lib-auth
- lib-events
- lib-observability
