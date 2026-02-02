# Connector Service

External system integration and data sync service with advanced scheduling, webhook handling, and credential management.

## Purpose
- Third-party system connectors
- Scheduled data synchronization (AUDIT_PULL, EXPORT, SYNC)
- Webhook event receiver with signature verification
- Secure credential resolution (ENV, Local DB Encrypted, AWS Secrets Manager)
- Data import/export with receipts
- API integration management

## Features

### 1. Scheduled Sync Engine
- **Distributed Scheduling**: Uses `SELECT FOR UPDATE SKIP LOCKED` for multi-instance deployments
- **Idempotent Execution**: Prevents duplicate runs for same fire time
- **Flexible Scheduling**: Support for both interval-based and cron expressions
- **Job Types**: AUDIT_PULL, EXPORT, SYNC
- **Run Receipts**: Store execution results and metrics in JSONB

### 2. Webhook Receiver
- **Endpoint**: `POST /api/v1/webhooks/{provider}/{connectorId}`
- **Signature Verification**: HMAC-SHA256 for GitHub, Stripe, and generic webhooks
- **Event Normalization**: Maps provider-specific events to common event types
- **Automatic Run Creation**: Creates connector run for each webhook event
- **Supported Providers**: GitHub, Stripe, and custom integrations

### 3. Credential Resolution
- **Strategy Pattern**: Three resolver types
  - **ENV**: Read from environment variables (development/testing)
  - **LOCAL_DB_ENCRYPTED**: AES-256 encrypted storage in PostgreSQL (default)
  - **AWS_SECRETS_MANAGER**: Real AWS Secrets Manager integration with LocalStack testing support (production)
- **AWS SDK v2**: Uses official AWS SDK for Secrets Manager
- **LocalStack Testing**: Full integration tests using Testcontainers + LocalStack (NO REAL AWS ACCOUNT REQUIRED)
- **Secure Storage**: Credentials encrypted at rest, never logged
- **Caching**: In-memory cache with configurable TTL (default 5 minutes)
- **Rotation Ready**: Support for credential rotation, versioning, and expiry tracking
- **Audit Trail**: All credential resolutions logged (SUCCEEDED/FAILED) without secret values
- **Fail Closed**: Configurable behavior when AWS resolution fails

### 4. Audit & Observability
- **Audit Events**: 
  - `SCHEDULE_FIRED`: When scheduled job triggers
  - `WEBHOOK_RECEIVED`: When webhook arrives
  - `CREDENTIALS_ACCESSED/STORED/DELETED`: Credential lifecycle (without logging values)
  - `CREDENTIAL_RESOLUTION_SUCCEEDED/FAILED`: AWS Secrets Manager resolution results
  - `CONNECTOR_RUN_CREATED/STARTED/FINISHED`: Run lifecycle events
- **Outbox Events**: All events published to outbox for downstream consumers
- **Run Receipts**: Detailed execution metrics stored per run
- **Scrubbed Errors**: Error messages automatically scrubbed to prevent secret leakage

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL (with JSONB for flexible payloads)
- Redis (caching for credentials)
- Kafka (events via outbox pattern)
- Spring Scheduling (distributed scheduler)

## Dependencies
- lib-common (audit, outbox)
- lib-events (event schemas)
- lib-observability (metrics, tracing)

## Database Schema

### Tables
1. **connectors**: Connector registry with auth config
2. **connector_targets**: Target endpoints for each connector
3. **connector_schedules**: Schedule definitions (cron or interval)
4. **connector_runs**: Unified run records (SCHEDULED, WEBHOOK, MANUAL)
5. **webhook_events**: Incoming webhook events with signature verification
6. **connector_credentials**: Encrypted credential storage
7. **connector_jobs**: Job execution records
8. **connector_job_logs**: Append-only execution logs
9. **outbox_events**: Outbox pattern for event publishing

## Configuration

```yaml
connector:
  # Credential resolver: ENV | LOCAL_DB_ENCRYPTED | AWS_SECRETS_MANAGER
  credentials:
    resolver: LOCAL_DB_ENCRYPTED
  
  # Scheduler settings
  scheduler:
    poll-interval-ms: 30000  # Poll every 30 seconds
    enabled: true

# AWS Secrets Manager configuration (when using AWS_SECRETS_MANAGER)
aws:
  secrets:
    enabled: false  # Set to true to enable AWS Secrets Manager
    region: us-east-1
    # Optional: For LocalStack or custom endpoint
    endpointOverride: http://localhost:4566
    cacheTtlSeconds: 300  # Cache TTL (5 minutes)
    # Fail closed: throw exception if AWS resolution fails (no fallback to local DB)
    failClosed: true
```

### Environment Variables

#### Credential Resolution
- `CONNECTOR_CREDENTIAL_RESOLVER`: Resolver type (default: LOCAL_DB_ENCRYPTED)
- `CONNECTOR_MASTER_KEY`: Base64-encoded AES-256 key for LOCAL_DB_ENCRYPTED
- `CONNECTOR_{UUID}_{TYPE}`: For ENV resolver (e.g., `CONNECTOR_abc123_API_KEY`)

#### AWS Secrets Manager Configuration
- `AWS_SECRETS_ENABLED`: Enable AWS Secrets Manager integration (default: false)
- `AWS_REGION`: AWS region for Secrets Manager (default: us-east-1)
- `AWS_SECRETS_ENDPOINT_OVERRIDE`: Custom endpoint for LocalStack or VPC endpoint
- `AWS_SECRETS_CACHE_TTL`: Cache TTL in seconds (default: 300)
- `AWS_SECRETS_FAIL_CLOSED`: Throw exception on failure vs. fallback (default: true)

#### AWS Credentials (production)
- `AWS_ACCESS_KEY_ID`: AWS credentials (or use IAM role)
- `AWS_SECRET_ACCESS_KEY`: AWS credentials (or use IAM role)
- Recommended: Use IAM roles in ECS/EKS instead of static credentials

#### Scheduler
- `CONNECTOR_SCHEDULER_ENABLED`: Enable/disable scheduler (default: true)

## AWS Secrets Manager Integration

### Production Setup

1. **Enable AWS Secrets Manager**:
```yaml
aws:
  secrets:
    enabled: true
    region: us-east-1
    cacheTtlSeconds: 300
    failClosed: true
```

2. **IAM Permissions Required**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret",
        "secretsmanager:CreateSecret",
        "secretsmanager:UpdateSecret",
        "secretsmanager:DeleteSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:regulyn/connector/*"
    }
  ]
}
```

3. **Secret Format** (JSON):
```json
{
  "accessToken": "oauth_access_token",
  "refreshToken": "oauth_refresh_token",
  "clientId": "client_id",
  "clientSecret": "client_secret",
  "apiKey": "api_key_value",
  "webhookSecret": "webhook_hmac_secret"
}
```

4. **Secret Naming Convention**:
```
regulyn/connector/{tenantId}/{connectorId}/{credentialType}
```

### LocalStack Testing (NO AWS ACCOUNT REQUIRED)

Run integration tests with LocalStack using Testcontainers:

```bash
# Run all tests including LocalStack integration tests
mvn test

# Run only AWS Secrets Manager tests
mvn test -Dtest=AwsSecretsManagerIntegrationTest
```

**LocalStack Configuration**:
- Uses `localstack/localstack:3.0` Docker image
- Secrets Manager service enabled
- Endpoint: `http://localhost:4566` (automatically configured by Testcontainers)
- No AWS credentials required
- Full feature parity with AWS Secrets Manager API

### Local Development with LocalStack

1. **Start LocalStack**:
```bash
docker run --rm -it -p 4566:4566 \
  -e SERVICES=secretsmanager \
  localstack/localstack:3.0
```

2. **Configure application.yml**:
```yaml
aws:
  secrets:
    enabled: true
    region: us-east-1
    endpointOverride: http://localhost:4566
    cacheTtlSeconds: 60
    failClosed: false  # Allow fallback during development
```

3. **Create test secret using AWS CLI**:
```bash
aws --endpoint-url=http://localhost:4566 secretsmanager create-secret \
  --name regulyn/connector/test-tenant/test-connector/OAUTH2 \
  --secret-string '{"accessToken":"test-token","clientId":"test-client"}'
```

### Database Schema (V6 Migration)

New columns in `connector_credentials` table:
```sql
- secret_provider (ENUM: LOCAL_DB_ENCRYPTED, AWS_SECRETS_MANAGER, ENV)
- secret_id (VARCHAR 500): AWS secret ARN or name
- secret_version (VARCHAR 100): Version ID (NULL = AWSCURRENT)
- last_resolved_at (TIMESTAMP): Last successful resolution
- resolve_fail_count (INTEGER): Consecutive failure count for monitoring
```

### Credential Resolution Flow

1. **Lookup**: Find credential metadata in database by `tenant_id`, `connector_id`, `credential_type`
2. **Provider Check**: Verify `secret_provider = AWS_SECRETS_MANAGER`
3. **Cache Check**: Check in-memory cache (key: tenantId:connectorId:secretId:version)
4. **AWS Fetch**: Call `GetSecretValue` with optional version
5. **Parse JSON**: Extract credential fields from secret value
6. **Update Metadata**: Set `last_resolved_at`, reset `resolve_fail_count`
7. **Audit**: Write `CREDENTIAL_RESOLUTION_SUCCEEDED` event
8. **Cache**: Store in memory for TTL duration
9. **Return**: Resolved credential with metadata

### Error Handling

- **ResourceNotFoundException**: Secret not found in AWS → increment `resolve_fail_count`, audit FAILED
- **Invalid JSON**: Secret value not valid JSON → throw exception, audit FAILED
- **Network Error**: AWS API timeout → increment fail count, retry based on `failClosed` setting
- **Scrubbing**: All error messages automatically scrubbed to prevent secret leakage

### Monitoring

Monitor credential resolution health:
```sql
-- Check credentials with high fail counts
SELECT connector_id, credential_type, secret_id, resolve_fail_count, last_resolved_at
FROM connector.connector_credentials
WHERE secret_provider = 'AWS_SECRETS_MANAGER'
  AND resolve_fail_count > 0
ORDER BY resolve_fail_count DESC;

-- Check stale credentials (not resolved in 24h)
SELECT connector_id, credential_type, secret_id, last_resolved_at
FROM connector.connector_credentials
WHERE secret_provider = 'AWS_SECRETS_MANAGER'
  AND (last_resolved_at IS NULL OR last_resolved_at < NOW() - INTERVAL '24 hours');
```
- `CONNECTOR_SCHEDULER_POLL_INTERVAL`: Poll interval in milliseconds (default: 30000)

## API Endpoints

### Webhook Receiver
```bash
# Receive webhook from GitHub
POST /api/v1/webhooks/github/{connectorId}
Headers:
  X-Hub-Signature-256: sha256={signature}
  X-Tenant-Id: {tenantId}
Body: {webhook payload}

# Receive webhook from Stripe
POST /api/v1/webhooks/stripe/{connectorId}
Headers:
  Stripe-Signature: t={timestamp},v1={signature}
  X-Tenant-Id: {tenantId}
Body: {webhook payload}

# Generic webhook
POST /api/v1/webhooks/custom/{connectorId}
Headers:
  X-Webhook-Signature: sha256={signature}
  X-Tenant-Id: {tenantId}
Body: {webhook payload}

# Health check for webhook configuration
GET /api/v1/webhooks/{provider}/{connectorId}/health
```

## Running the Service

### Local Development
```bash
cd services/connector-service
mvn spring-boot:run
```

### Run Tests
```bash
mvn clean test
```

### Docker
```bash
docker build -t regulyn/connector-service .
docker run -p 8093:8093 regulyn/connector-service
```

## Security Considerations

1. **Credential Storage**
   - Never log credential values
   - Use LOCAL_DB_ENCRYPTED with strong master key in production
   - Rotate credentials regularly
   - Use AWS_SECRETS_MANAGER for production deployments

2. **Webhook Verification**
   - Always verify signatures for production webhooks
   - Store webhook secrets securely using credential resolver
   - Implement rate limiting for webhook endpoints

3. **Scheduler**
   - Use SELECT FOR UPDATE SKIP LOCKED for distributed locking
   - Implement idempotency keys to prevent duplicate runs
   - Monitor for stuck or failed runs

## Testing

### Integration Tests
```java
// Test scheduler idempotency
@Test
void testSchedulerIdempotency()

// Test webhook signature verification
@Test
void testWebhookSignatureVerification()

// Test run receipts
@Test
void testConnectorRunReceipts()

// Test credential resolvers
@Test
void testCredentialResolvers()
```

### Manual Testing

#### Create Schedule
```bash
# Via SchedulerService
schedulerService.createSchedule(
  tenantId,
  connectorId,
  targetId,
  "AUDIT_PULL",
  null,        // cronExpr
  3600         // Run every hour
);
```

#### Send Webhook
```bash
curl -X POST http://localhost:8093/api/v1/webhooks/github/connector-uuid \
  -H "X-Hub-Signature-256: sha256=..." \
  -H "X-Tenant-Id: tenant-uuid" \
  -H "Content-Type: application/json" \
  -d '{"action":"push","repository":"test-repo"}'
```

## Monitoring

### Metrics
- `connector.scheduler.runs.created`: Number of runs created by scheduler
- `connector.webhook.events.received`: Number of webhooks received
- `connector.webhook.signature.verified`: Signature verification success rate
- `connector.credentials.accessed`: Credential access count
- `connector.runs.duration`: Run execution duration

### Health Checks
- Scheduler: Check for stuck schedules
- Webhooks: Verify signature verification rate
- Credentials: Monitor credential rotation status

## Troubleshooting

### Scheduler Not Running
1. Check `connector.scheduler.enabled=true` in configuration
2. Verify PostgreSQL connection
3. Check logs for schedule processing errors
4. Verify schedules have `enabled=true` and `next_run_at` is in the past

### Webhook Signature Failures
1. Verify webhook secret stored correctly: `credentialService.resolveCredential(...)`
2. Check signature format matches provider (GitHub: `sha256=`, Stripe: `t=,v1=`)
3. Ensure payload is raw string, not parsed JSON
4. Verify timestamp tolerance for Stripe webhooks

### Credential Resolution Errors
1. Check `CONNECTOR_CREDENTIAL_RESOLVER` is set correctly
2. For LOCAL_DB_ENCRYPTED, verify `CONNECTOR_MASTER_KEY` is set
3. For AWS_SECRETS_MANAGER, check AWS credentials and permissions
4. Review logs for credential access audit events

## Future Enhancements
- Spring Cloud Config for credential management
- Cron expression parser (spring-context-support)
- Webhook replay mechanism
- Run history retention policy
- Dead letter queue for failed runs
- Credential rotation automation
