# Connector Service – Round 2 Hardening

## 1) Purpose
Connector-service is the **connector execution plane**. It securely resolves credentials, schedules and receives work, executes connector runs via adapters, updates cursor state, and emits audit/outbox events for downstream processing.

## 2) Data model (Round 2 tables)
### connector_credentials
- **provider** (ENV / LOCAL_DB_ENCRYPTED / AWS_SECRETS_MANAGER)
- **enc_payload / enc_iv / enc_kid**: encrypted credential payload
- **secret_id / secret_version**: external secret metadata (AWS / ENV mapping)

### connector_runs
- **status**: PENDING → RUNNING → SUCCEEDED | FAILED_RETRYABLE | FAILED_TERMINAL
- **attempts / max_attempts**: retry tracking
- **next_retry_at**: next retry time (exponential backoff)
- **schedule_id**: nullable (manual runs allowed)
- **correlation_id**: max length 64, non-null
- **evidence_artifact_ref**: nullable reference to evidence artifact

### connector_schedules
- **cron / timezone**: schedule definition
- **next_fire_at / last_fire_at**: execution tracking
- **enabled**: disabled on invalid cron

### webhook_events
- **signature_valid**: verification result
- **payload_hash**: SHA-256 hash of raw payload
- **headers_json / raw_payload_json**: JSONB storage
- **normalized_type / normalized_subject**: provider-specific normalization

### connector_cursor_state
- **cursor_json**: JSONB cursor
- **unique** on (tenant_id, connector_id, target_id, job_type)

## 3) State machines
### connector_runs
- **PENDING** → **RUNNING** (claimed)
- **RUNNING** → **SUCCEEDED** (success)
- **RUNNING** → **FAILED_RETRYABLE** (transient failure)
- **RUNNING** → **FAILED_TERMINAL** (max attempts)
- **RUNNING (stuck)** → **FAILED_RETRYABLE** + next_retry_at

### schedules
- **enabled=true** → fires when `next_fire_at <= now()`
- invalid cron → **enabled=false** + SCHEDULE_DISABLED_DUE_TO_ERROR

### webhooks
- receive → verify → normalize → store → emit events
- duplicates: detected by payload hash (10-minute window)

## 4) Events emitted (audit + outbox)
### Runs
- RUN_STARTED
- RUN_SUCCEEDED
- RUN_FAILED_RETRYABLE
- RUN_FAILED_TERMINAL
- RUN_STUCK_RETRYABLE
- RUN_EVIDENCE_STORED

### Schedules
- SCHEDULE_FIRED
- RUN_CREATED
- SCHEDULE_DISABLED_DUE_TO_ERROR

### Webhooks
- WEBHOOK_RECEIVED
- WEBHOOK_SIGNATURE_INVALID
- WEBHOOK_NORMALIZED

### Credential resolution
- CREDENTIAL_RESOLVE_STARTED
- CREDENTIAL_RESOLVE_SUCCEEDED
- CREDENTIAL_RESOLVE_FAILED

**Required fields** (audit + outbox payload):
- tenant_id, connector_id, run_id (if any), target_id (if any), job_type (if any), correlation_id

## 5) Credential providers
- **ENV**: env-var mapping stored encrypted in DB
- **LOCAL_DB_ENCRYPTED**: encrypted credential JSON
- **AWS_SECRETS_MANAGER**: secret metadata stored encrypted in DB, resolved via AWS

**Security rules**:
- No secrets in logs
- No secrets in outbox/audit payloads

## 6) Ops / Config
### Connector scheduling
- `connector.scheduler.batch-size` (default 50)

### Run worker
- `connector.run-worker.batch-size` (default 50)
- `connector.run-worker.stuck-timeout-minutes` (default 15)

### Credentials
- `connector.credentials.encryption.key` (required for LOCAL_DB_ENCRYPTED)
- `connector.credentials.aws.enabled` (default false)
- `connector.credentials.aws.region` (default us-east-1)
- `connector.credentials.aws.secrets-endpoint`
- `connector.credentials.aws.access-key`
- `connector.credentials.aws.secret-key`

### Evidence
- `regulyn.evidence.baseUrl` (default http://localhost:8083)

### LocalStack testing
- Enable AWS in tests via LocalStack
- Ensure `connector.credentials.encryption.key` is set

## 7) Failure modes + guarantees
- **Fail-closed** for AWS secrets when disabled
- **Retries** with exponential backoff: 1m, 5m, 15m, 30m, 60m (cap)
- **Stuck-run**: RUNNING > timeout → FAILED_RETRYABLE
- **Idempotency**:
  - schedule run duplication window: ±1 minute
  - webhook duplicate window: 10 minutes (payload hash)

## 8) Deployment checklist
- Flyway migrations applied (V1–V9)
- Encryption key configured
- Evidence service reachable
- AWS secrets configured if enabled
- Scheduled tasks enabled in runtime
- Run worker enabled
- Outbox/audit pipelines consuming events
