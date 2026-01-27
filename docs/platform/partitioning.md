# Database Partitioning Strategy for Regulyn Platform

## Overview

This document outlines the **table partitioning strategy** for the Regulyn multi-tenant SaaS platform. Partitioning is used to improve query performance, simplify data lifecycle management, and enable future tenant data isolation at the storage level.

## Current Implementation: Tenant-Based Row Filtering

**Status:** Production (v0.1)

All tables include a `tenant_id UUID` column for multi-tenancy. Data is logically isolated via:

1. **Application Layer:** Every query includes `WHERE tenant_id = ?` filter from `TenantContext`
2. **Database Indexes:** Composite indexes on `(tenant_id, ...)` for efficient filtering
3. **Foreign Keys:** All relationships include `tenant_id` for referential integrity

**Tables with Multi-Tenancy:**
- `tenants` (base table, no tenant_id needed)
- `users` (tenant_id FK to tenants)
- `roles` (tenant_id FK to tenants)
- `user_roles` (tenant_id denormalized for efficient queries)
- `api_keys` (tenant_id FK to tenants)
- All future domain tables (requests, processing_records, data_subjects, consents, etc.)

## Future: Partition by Tenant ID (Optional)

**When to Consider:**
- Tenant count > 1,000 and growing
- Specific tenants have > 10M rows in a single table
- Need physical data separation for compliance (e.g., EU vs US data residency)
- Data lifecycle management requires per-tenant operations (archival, deletion)

### PostgreSQL Native Partitioning

**Approach:** Use PostgreSQL 10+ declarative partitioning with `PARTITION BY LIST (tenant_id)`

**Example for `users` table:**

```sql
-- Create partitioned parent table
CREATE TABLE users (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    UNIQUE (tenant_id, email)
) PARTITION BY LIST (tenant_id);

-- Create partitions for specific tenants
CREATE TABLE users_tenant_11111111 PARTITION OF users
    FOR VALUES IN ('11111111-1111-1111-1111-111111111111');

CREATE TABLE users_tenant_22222222 PARTITION OF users
    FOR VALUES IN ('22222222-2222-2222-2222-222222222222');

-- Default partition for new tenants
CREATE TABLE users_default PARTITION OF users DEFAULT;

-- Indexes on each partition (automatically inherited)
CREATE INDEX idx_users_tenant_email ON users (tenant_id, email);
```

**Benefits:**
- Query planner automatically prunes partitions based on `tenant_id` in WHERE clause
- Faster queries for high-volume tenants (smaller index scans)
- Can move partition to different tablespace (e.g., SSD vs HDD, different AWS region)
- Simplify tenant data deletion: `DROP TABLE users_tenant_xyz CASCADE;`

**Drawbacks:**
- Operational complexity: Need automation to create partitions for new tenants
- Foreign keys between partitioned tables have limitations in PostgreSQL < 15
- Global unique constraints (e.g., on `user_id`) require careful design

## Row-Level Security (RLS) - Alternative Approach

**Status:** Optional (can be added later)

PostgreSQL RLS provides defense-in-depth by enforcing `tenant_id` filtering at the database level.

**Example for `users` table:**

```sql
-- Enable RLS on table
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Create policy: users can only see rows for their tenant
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Application sets session variable before queries
SET app.current_tenant_id = '11111111-1111-1111-1111-111111111111';
```

**Benefits:**
- Defense against SQL injection or ORM bugs that omit `tenant_id` filter
- Compliance: Audit logs can prove tenant data never leaks at DB level

**Drawbacks:**
- Performance overhead: RLS policies evaluated for every row
- Requires setting session variable for each connection (use connection pool init)
- Debugging complexity: Queries return empty if session variable not set

## Recommendations

### Phase 1: Current (v0.1-0.4)
✅ **Use row-based filtering with composite indexes**
- Simple, proven approach for early-stage multi-tenant SaaS
- No operational complexity
- Sufficient for 100-500 tenants with < 1M rows per table

### Phase 2: Growth (v1.0+)
⚠️ **Consider partitioning IF:**
- Single tenant has > 10M rows in a table
- Query performance degrades despite proper indexing
- Need tenant-specific data residency (EU/US compliance)

**Migration Strategy:**
1. Partition new high-volume tables (e.g., `processing_records`, `audit_logs`)
2. Use Flyway migration to convert existing tables (requires downtime or blue-green deployment)
3. Automate partition creation for new tenants (stored procedure or application trigger)

### Phase 3: Enterprise (v2.0+)
🔒 **Add RLS for defense-in-depth**
- Enable RLS on sensitive tables (`users`, `consents`, `data_subjects`)
- Keeps row-based filtering in application (performance)
- RLS acts as safety net for accidental tenant leakage

## Performance Monitoring

**Key Metrics to Track:**
1. Query response time for multi-tenant tables (p50, p95, p99)
2. Index usage statistics: `pg_stat_user_indexes`
3. Table bloat and vacuum frequency
4. Tenant-specific row counts: `SELECT tenant_id, COUNT(*) FROM users GROUP BY tenant_id;`

**Alert Thresholds:**
- Any tenant with > 5M rows in a single table → Consider partitioning
- Query p95 > 100ms for tenant-filtered queries → Review indexes
- Table bloat > 20% → Review vacuum settings

## References

- [PostgreSQL Partitioning Docs](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [PostgreSQL RLS](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [Multi-Tenant Data Architecture](https://learn.microsoft.com/en-us/azure/architecture/guide/multitenant/considerations/tenancy-models)

---

**Last Updated:** Platform Foundations v0.1  
**Next Review:** After first production tenant reaches 1M rows in any table
