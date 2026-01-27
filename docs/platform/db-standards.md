# Database Standards & Conventions

## Overview

All Regulyn services use PostgreSQL as the primary database with Flyway for schema migrations. Each service has its own dedicated schema within a shared PostgreSQL instance.

## Architecture

### Single Database, Multiple Schemas

- **Database**: `regulyn` (single instance)
- **Connection**: `jdbc:postgresql://localhost:5432/regulyn`
- **Credentials**: `regulyn` / `regulyn` (local dev)
- **Schema per Service**: Each service owns its dedicated schema

### Schema Naming Convention

| Service | Schema Name |
|---------|-------------|
| identity-tenant-service | `identity` |
| consent-service | `consent` |
| ropa-inventory-service | `ropa` |
| dsar-grievance-service | `dsar` |
| retention-deletion-service | `deletion` |
| incident-breach-service | `incident` |
| nominee-service | `nominee` |
| children-guardian-service | `guardian` |
| vendor-sharing-service | `vendor` |
| employee-data-service | `employee` |
| evidence-reporting-service | `evidence` |
| notification-service | `notification` |
| connector-service | `connector` |
| scanner-service | `scanner` |

## Table Design Standards

### Naming Conventions

- **Table Names**: `snake_case` (e.g., `audit_events`, `service_meta`)
- **Column Names**: `snake_case` (e.g., `tenant_id`, `created_at`)
- **Primary Keys**: `{table_singular}_id` (e.g., `tenant_id`, `event_id`)
- **Foreign Keys**: Match referenced column name (e.g., `tenant_id` FK to `tenants.tenant_id`)

### Standard Columns

Every entity table should include:

```sql
-- Primary key (UUID)
{entity}_id UUID PRIMARY KEY DEFAULT gen_random_uuid()

-- Tenant isolation (for multi-tenant tables)
tenant_id UUID NOT NULL

-- Audit timestamps (use TIMESTAMPTZ for timezone awareness)
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at TIMESTAMPTZ
created_by UUID
updated_by UUID
```

### Data Types

- **Primary Keys**: `UUID` with `DEFAULT gen_random_uuid()`
- **Timestamps**: `TIMESTAMPTZ` (never `TIMESTAMP` without time zone)
- **Text**: `TEXT` for variable-length strings (avoid `VARCHAR` limits unless needed for business constraints)
- **JSON**: `JSONB` for structured data (better indexing than `JSON`)
- **Booleans**: `BOOLEAN` (not `CHAR(1)` or `INTEGER`)
- **Enums**: `TEXT` with `CHECK` constraints (more flexible than PostgreSQL enums)

### Required Tables per Service

Each service must have:

1. **service_meta**: Service metadata table
   ```sql
   CREATE TABLE service_meta (
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       tenant_id UUID,
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       name TEXT NOT NULL,
       version TEXT NOT NULL
   );
   ```

2. **audit_events**: Append-only audit log (see Audit Standards below)

## Audit Standards

### Audit Events Table

Every service must have an `audit_events` table:

```sql
CREATE TABLE audit_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_type TEXT NOT NULL,
    service TEXT NOT NULL DEFAULT '{service-name}',
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    evidence_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB
);

-- Required indexes
CREATE INDEX idx_audit_events_tenant_occurred ON audit_events(tenant_id, occurred_at);
CREATE INDEX idx_audit_events_tenant_entity ON audit_events(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_events_tenant_action ON audit_events(tenant_id, action);
```

### Audit Event Fields

- **event_id**: Unique event identifier
- **tenant_id**: Tenant who owns this event
- **occurred_at**: When the action happened (indexed)
- **actor_id**: User or API key that performed the action
- **actor_type**: One of: `USER`, `API_KEY`, `SYSTEM`
- **service**: Which service recorded this event
- **action**: What action was performed (e.g., `CREATE_CONSENT`, `DELETE_ROPA`)
- **entity_type**: Type of entity affected (e.g., `CONSENT`, `ROPA_RECORD`)
- **entity_id**: ID of the specific entity
- **payload_hash**: SHA-256 hash of request/payload for tamper detection
- **evidence_id**: Optional link to evidence record
- **metadata**: Additional context (JSON format)

### Append-Only Policy

Audit tables are **append-only**:
- No UPDATE or DELETE operations allowed in application code
- Use database-level permissions to enforce (future)
- Retention handled by monthly partition archival (not deletion)

## Indexing Guidelines

### Primary Indexes

1. **Primary Keys**: Automatically indexed
2. **Foreign Keys**: Always index foreign keys
   ```sql
   CREATE INDEX idx_{table}_{referenced_table} ON {table}({foreign_key});
   ```

3. **Multi-Tenant Queries**: Composite index with tenant_id first
   ```sql
   CREATE INDEX idx_{table}_tenant_{column} ON {table}(tenant_id, {column});
   ```

4. **Timestamp Queries**: Include in composite with tenant_id
   ```sql
   CREATE INDEX idx_{table}_tenant_created ON {table}(tenant_id, created_at);
   ```

### Performance Considerations

- **Selective Indexes**: Only index columns used in WHERE, ORDER BY, or JOIN clauses
- **Partial Indexes**: Use for filtering common query patterns
  ```sql
  CREATE INDEX idx_active_records ON records(tenant_id) WHERE status = 'ACTIVE';
  ```
- **JSONB Indexes**: Use GIN indexes for JSONB columns with complex queries
  ```sql
  CREATE INDEX idx_{table}_metadata ON {table} USING GIN(metadata);
  ```

## Migration Standards (Flyway)

### File Naming

- **Location**: `src/main/resources/db/migration`
- **Pattern**: `V{version}__{description}.sql`
- **Examples**:
  - `V1__init.sql` - Initial schema and tables
  - `V2__add_consent_status.sql` - Add status column to consent table
  - `V3__evidence_artifacts.sql` - Add evidence artifacts table

### Migration Structure

```sql
-- Always set search path first
SET search_path TO {schema_name};

-- Then execute DDL
CREATE TABLE ...
CREATE INDEX ...

-- Add data if needed
INSERT INTO ...
```

### Best Practices

1. **Never modify existing migrations** - create new ones
2. **Test migrations** on copy of production data
3. **Keep migrations idempotent** where possible (use IF NOT EXISTS)
4. **Single responsibility** - one migration per feature
5. **Comment complex logic** in migration files

## Partitioning Strategy

### Monthly Partitioning (Future Implementation)

For high-volume tables (especially `audit_events`):

```sql
-- Declarative partitioning by month
CREATE TABLE audit_events (
    -- columns...
    occurred_at TIMESTAMPTZ NOT NULL
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions
CREATE TABLE audit_events_2026_01 PARTITION OF audit_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
```

**Retention Policy** (Documented - Not Yet Implemented):
- Keep partitions for 7 years (compliance requirement)
- Archive old partitions to object storage monthly
- Never delete audit data (archive only)

## Query Patterns

### Tenant Isolation

**ALWAYS** filter by tenant_id first:

```sql
-- Good
SELECT * FROM records 
WHERE tenant_id = ? AND status = 'ACTIVE';

-- Bad (missing tenant filter)
SELECT * FROM records WHERE status = 'ACTIVE';
```

### Pagination

Use keyset pagination for better performance:

```sql
-- Keyset pagination (preferred)
SELECT * FROM records
WHERE tenant_id = ? AND created_at > ?
ORDER BY created_at
LIMIT 100;

-- Avoid OFFSET pagination for large result sets
```

### Joins

Always include tenant_id in JOIN conditions:

```sql
SELECT e.*, a.*
FROM evidence_records e
JOIN evidence_artifacts a ON 
    a.evidence_id = e.evidence_id AND
    a.tenant_id = e.tenant_id
WHERE e.tenant_id = ?;
```

## Schema Evolution

### Adding Columns

```sql
-- Safe: Add nullable column
ALTER TABLE records ADD COLUMN new_field TEXT;

-- Safe: Add with default
ALTER TABLE records ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING';
```

### Removing Columns

**Never drop columns directly** - use multi-phase approach:

1. Phase 1: Stop using column in code
2. Phase 2: Deploy and verify
3. Phase 3: Drop column in new migration

### Changing Types

Use multi-phase approach:

1. Add new column with new type
2. Migrate data
3. Update code to use new column
4. Drop old column

## Testing Standards

### Migration Testing

1. **Forward Migration**: Run migrations on empty database
2. **Idempotency**: Run migrations twice (should succeed)
3. **Rollback Test**: Test Flyway undo migrations (future)
4. **Data Migration**: Verify data integrity after structural changes

### Performance Testing

- Test queries with realistic data volumes
- Verify index usage with `EXPLAIN ANALYZE`
- Monitor query performance in staging environment

## Configuration

### Hibernate Settings

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # NEVER use 'update' or 'create'
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
        default_schema: {service_schema}
```

### Flyway Settings

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    schemas: {service_schema}
    locations: classpath:db/migration
```

## Development Workflow

1. **Start PostgreSQL**: `docker-compose -f docker-compose.dev.yml up postgres`
2. **Write Migration**: Create `V{n}__{description}.sql` in service
3. **Test Locally**: Run service - Flyway auto-migrates
4. **Verify Schema**: Check with `psql` or pgAdmin
5. **Commit Migration**: Include in PR with code changes

## Production Considerations

### Deployment

- Migrations run automatically on service startup
- Use `baseline-on-migrate: true` for existing databases
- Monitor migration duration (long migrations block startup)
- Consider maintenance windows for breaking changes

### Monitoring

- Track migration status in Flyway schema history table
- Monitor query performance via `pg_stat_statements`
- Alert on slow queries (> 1 second)
- Track table sizes and growth rates

### Backup & Recovery

- PostgreSQL continuous archiving (WAL)
- Daily full backups
- Point-in-time recovery capability
- Test restore procedures quarterly

## Security

### Connection Security

- Use connection pooling (HikariCP default)
- Encrypt connections in production (SSL/TLS)
- Rotate database passwords quarterly
- Use separate credentials per environment

### Row-Level Security (Future)

PostgreSQL RLS for additional tenant isolation:

```sql
ALTER TABLE records ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON records
    USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

## References

- PostgreSQL Documentation: https://www.postgresql.org/docs/
- Flyway Documentation: https://flywaydb.org/documentation/
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
