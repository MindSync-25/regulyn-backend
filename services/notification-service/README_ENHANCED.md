# Notification Service - ENHANCED

Multi-channel notification and communication management service with **production-grade delivery tracking, retry mechanisms, consent enforcement, and evidence generation**.

## 🚀 NEW FEATURES (Round 2 - Hardening)

### ✅ Real Provider Integration
- **SMTP Email Provider**: Production-ready JavaMail integration with custom headers and tracking
- **Local Log Provider**: Development/testing provider that logs to console
- **Provider abstraction**: Easy to add SES, SendGrid, Twilio, etc.

### ✅ Delivery Tracking & Callbacks
- **delivery_receipts** table: Tracks provider callbacks (DELIVERED, BOUNCED, COMPLAINED)
- **Delivery callback endpoint**: `POST /api/v1/notifications/delivery/status`
- **Provider message IDs**: Full correlation between our system and provider systems
- **Delivery status updates**: QUEUED → SENT → DELIVERED/FAILED/BOUNCED

### ✅ Retry Engine with Exponential Backoff
- **Automated retry scheduler**: Runs every 60 seconds
- **Exponential backoff**:
  - Attempt 1: Immediate
  - Attempt 2: 1 minute after first failure
  - Attempt 3: 5 minutes after second failure
  - Attempt 4+: 15 minutes (max)
- **Terminal failure**: After max attempts (default: 3)
- **Idempotency**: Duplicate messages blocked by hash + recipient + date

### ✅ Consent Enforcement
- **ConsentServiceClient**: Integration with consent-service API
- **Pre-send validation**: Blocks sends without OPT_IN consent
- **Fail-safe**: Blocks on consent service error (GDPR-safe)
- **CONSENT_BLOCKED status**: Messages blocked are tracked and audited
- **Batch consent check**: Optimized for multi-recipient sends

### ✅ Evidence Linkage
- **EvidenceServiceClient**: Integration with evidence-reporting-service
- **Proof artifacts**: NOTIFICATION_SENT, NOTIFICATION_DELIVERED
- **Artifact IDs stored**: evidence_artifact_id in notification_messages table
- **Compliance ready**: Full chain of custody for DPDP/GDPR audits

### ✅ Enhanced Audit & Events
Six new event types:
1. **NOTIFICATION_QUEUED**: Message created and queued for send
2. **NOTIFICATION_SENT**: Successfully sent to provider
3. **NOTIFICATION_CONSENT_BLOCKED**: Blocked due to missing consent
4. **DELIVERY_UPDATED**: Provider delivered/failed/bounced callback
5. **NOTIFICATION_FAILED_TERMINAL**: Max retries exhausted
6. **NOTIFICATION_SENT_RETRY**: Retry attempt succeeded

---

## 📊 Database Schema (New Tables)

### notification_messages
Core message table with retry tracking:
```sql
- message_id (PK)
- tenant_id, request_id, recipient_id, recipient_address
- channel, message_subject, message_body, message_format
- message_hash (SHA-256 for idempotency)
- status (PENDING, QUEUED, SENT, DELIVERED, FAILED_RETRYABLE, FAILED_TERMINAL, CONSENT_BLOCKED)
- provider_message_id, provider_name
- attempt_count, max_attempts, next_retry_at, last_attempt_at
- last_error_message, last_error_code
- evidence_artifact_id (link to evidence service)
- created_at, updated_at, created_by
```

**Indexes:**
- tenant_id, request_id, recipient_id
- status, message_hash, next_retry_at
- provider_message_id (for callbacks)

### delivery_receipts
Provider callback tracking:
```sql
- receipt_id (PK)
- tenant_id, dispatch_id
- provider_message_id (correlation with provider)
- delivery_status (QUEUED, SENT, DELIVERED, FAILED, BOUNCED, COMPLAINED)
- provider_name, provider_event_type
- provider_callback_data (JSONB - full webhook payload)
- error_message, error_code
- updated_at
```

---

## 🔧 Configuration

### Environment Variables

```yaml
# SMTP Provider (Production)
NOTIFICATION_SMTP_ENABLED=true
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USERNAME=apikey
SMTP_PASSWORD=SG.xxxxxxxxxxxx
SMTP_AUTH=true
SMTP_STARTTLS=true
NOTIFICATION_FROM_ADDRESS=noreply@regulyn.com
NOTIFICATION_FROM_NAME=Regulyn Notifications

# Local Log Provider (Development)
NOTIFICATION_LOCAL_LOG_ENABLED=false

# Consent Service Integration
NOTIFICATION_CONSENT_ENABLED=true
CONSENT_SERVICE_URL=http://consent-service:8080

# Evidence Service Integration
NOTIFICATION_EVIDENCE_ENABLED=true
EVIDENCE_SERVICE_URL=http://evidence-reporting-service:8080
```

### Provider Selection
- **SMTP enabled + Local disabled**: Uses SMTP for EMAIL channel
- **SMTP disabled + Local enabled**: Uses LocalLogProvider for EMAIL channel
- **Both disabled**: No EMAIL provider available (error on send)

---

## 🎯 API Endpoints

### Send Notification (Enhanced)
```http
POST /api/v1/notifications/send
Content-Type: application/json
Authorization: Bearer <tenant-jwt>

{
  "requestRef": "incident-breach-789",  // Optional idempotency key
  "templateKey": "BREACH_NOTICE",
  "channel": "EMAIL",
  "language": "en",
  "audience": {
    "type": "DATA_PRINCIPAL",
    "dataPrincipalId": "dp-user-456"
  },
  "variables": {
    "userName": "John Doe",
    "incidentId": "INC-2026-001"
  }
}
```

**Response (200 OK):**
```json
{
  "requestId": "uuid",
  "totalRecipients": 1,
  "sentCount": 1,
  "skippedCount": 0,
  "dispatches": [
    {
      "recipientId": "dp-user-456",
      "status": "SENT",  // or "QUEUED", "CONSENT_BLOCKED", "FAILED"
      "reason": null
    }
  ]
}
```

### Delivery Status Callback
```http
POST /api/v1/notifications/delivery/status
Content-Type: application/json

{
  "providerMessageId": "smtp-msg-123",
  "deliveryStatus": "DELIVERED",  // DELIVERED | FAILED | BOUNCED | COMPLAINED
  "providerName": "SMTP",
  "eventType": "Delivery",
  "errorMessage": null,
  "errorCode": null,
  "callbackData": {
    "timestamp": "2026-01-31T16:00:00Z",
    "smtpCode": "250"
  }
}
```

**Response (200 OK):**
```json
{
  "status": "updated",
  "messageId": "uuid"
}
```

---

## 🔄 Retry Workflow

### Retry Strategy
```
Attempt 1: Immediate send
  ↓ (FAILS)
Attempt 2: Wait 1 minute
  ↓ (FAILS)
Attempt 3: Wait 5 minutes
  ↓ (FAILS)
Status: FAILED_TERMINAL (max attempts reached)
```

### Retry Scheduler
- **Frequency**: Every 60 seconds
- **Query**: Finds messages with `status = FAILED_RETRYABLE` AND `next_retry_at <= NOW` AND `attempt_count < max_attempts`
- **Execution**: Re-sends via provider, updates attempt count, calculates next retry time
- **Terminal**: After max attempts, sets `status = FAILED_TERMINAL`
- **Events**: Emits `NOTIFICATION_SENT_RETRY` (success) or `NOTIFICATION_FAILED_TERMINAL` (exhausted)

---

## 🛡️ Consent Enforcement

### Pre-Send Validation
1. **Check consent-service API**: `GET /api/v1/consent/check?tenantId={}&dataPrincipalId={}&channel={}&category={}`
2. **Response**: `{ "hasConsent": true/false, "status": "OPT_IN|OPT_OUT", "reason": "..." }`
3. **Logic**:
   - `hasConsent = true` → Allow send
   - `hasConsent = false` → Block send, set `status = CONSENT_BLOCKED`, audit event
   - **Fail-safe**: If consent service error → **BLOCK** (GDPR-safe default)

### Consent Blocking Flow
```
User: dp-user-123
Channel: EMAIL
Category: MARKETING
Consent: OPT_OUT

1. Create notification_message with status = CONSENT_BLOCKED
2. Audit event: NOTIFICATION_CONSENT_BLOCKED
3. Outbox event: notification.consent.blocked.v1
4. Response: { status: "CONSENT_BLOCKED", reason: "No OPT_IN consent" }
5. NO EMAIL SENT
```

---

## 📜 Evidence Generation

### Artifact Creation
**When**: On successful send to provider  
**Who**: EvidenceServiceClient  
**What**: 
- **Artifact Type**: NOTIFICATION_SENT
- **Artifact Data**:
  ```json
  {
    "messageId": "uuid",
    "recipient": "user@example.com",
    "channel": "EMAIL",
    "subject": "Breach Notice",
    "providerMessageId": "smtp-msg-456",
    "timestamp": 1738338000000
  }
  ```
- **Stored**: evidence_artifact_id in notification_messages table

### Audit Trail
Full chain of custody:
1. **Message created** → `NOTIFICATION_QUEUED` event
2. **Send attempted** → `NOTIFICATION_SENT` event + evidence artifact created
3. **Delivery confirmed** → `DELIVERY_UPDATED` event + evidence artifact updated
4. **Query**: `SELECT * FROM notification_messages WHERE evidence_artifact_id = 'artifact-123'`

---

## 📈 Event Types & Payloads

### NOTIFICATION_QUEUED
```json
{
  "eventType": "notification.queued.v1",
  "payload": {
    "messageId": "uuid",
    "recipientAddress": "user@example.com",
    "channel": "EMAIL"
  }
}
```

### NOTIFICATION_SENT
```json
{
  "eventType": "notification.sent.v1",
  "payload": {
    "messageId": "uuid",
    "providerMessageId": "smtp-msg-123",
    "recipientAddress": "user@example.com",
    "channel": "EMAIL",
    "evidenceArtifactId": "evidence-456"
  }
}
```

### NOTIFICATION_CONSENT_BLOCKED
```json
{
  "eventType": "notification.consent.blocked.v1",
  "payload": {
    "messageId": "uuid",
    "recipientId": "dp-user-789",
    "channel": "EMAIL",
    "category": "MARKETING"
  }
}
```

### DELIVERY_UPDATED
```json
{
  "eventType": "notification.delivery.updated.v1",
  "payload": {
    "messageId": "uuid",
    "providerMessageId": "smtp-msg-123",
    "deliveryStatus": "DELIVERED",
    "updatedAt": "2026-01-31T16:00:00Z"
  }
}
```

### NOTIFICATION_FAILED_TERMINAL
```json
{
  "eventType": "notification.failed.terminal.v1",
  "payload": {
    "messageId": "uuid",
    "recipientAddress": "user@example.com",
    "channel": "EMAIL",
    "attemptCount": 3,
    "errorMessage": "SMTP connection timeout"
  }
}
```

### NOTIFICATION_SENT_RETRY
```json
{
  "eventType": "notification.sent.retry.v1",
  "payload": {
    "messageId": "uuid",
    "recipientAddress": "user@example.com",
    "channel": "EMAIL",
    "attemptCount": 2,
    "providerMessageId": "smtp-msg-retry-456"
  }
}
```

---

## 🧪 Testing

### Integration Tests
Comprehensive tests with:
- **Testcontainers**: PostgreSQL 15
- **GreenMail**: In-memory SMTP server for testing
- **Mock clients**: ConsentServiceClient, EvidenceServiceClient

**Test Scenarios:**
1. ✅ `testSmtpEmailSendSuccess`: Real SMTP send via GreenMail
2. ✅ `testConsentBlocking`: Consent service blocks send
3. ✅ `testIdempotency_DuplicateMessageHash`: Duplicate message blocked
4. ✅ `testRetryMechanism_FailedMessage`: Retry scheduling logic
5. ✅ `testEvidenceArtifactCreation`: Evidence artifact ID stored

### Run Tests
```bash
cd services/notification-service
mvn test
```

---

## 🚀 Running Locally

### Prerequisites
- PostgreSQL 15+ with `notification` schema
- SMTP server (or use LocalLogProvider)

### With LocalLogProvider (Development)
```bash
# application-local.yml
notification:
  smtp:
    enabled: false
  local-log:
    enabled: true
  consent:
    enabled: false  # Disable for local testing
  evidence:
    enabled: false  # Disable for local testing

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### With SMTP (Production)
```bash
# Set environment variables
export NOTIFICATION_SMTP_ENABLED=true
export SMTP_HOST=smtp.gmail.com
export SMTP_PORT=587
export SMTP_USERNAME=your-email@gmail.com
export SMTP_PASSWORD=your-app-password
export SMTP_AUTH=true
export SMTP_STARTTLS=true

mvn spring-boot:run
```

---

## 📦 Deployment

### Docker
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/notification-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes (via Helm)
```bash
helm upgrade --install notification-service \
  ../../helm/charts/notification \
  --set smtp.enabled=true \
  --set smtp.host=smtp.sendgrid.net \
  --set consent.enabled=true \
  --set evidence.enabled=true
```

---

## 🔍 Monitoring & Observability

### Key Metrics
- `notification.sent.total`: Total sent counter
- `notification.failed.total`: Total failed counter
- `notification.consent_blocked.total`: Total consent blocked counter
- `notification.retry.total`: Total retry attempts
- `notification.delivery.status{status=DELIVERED}`: Delivery status gauges

### Health Checks
- `GET /actuator/health`: Service health
- `GET /actuator/metrics/notification.sent.total`: Prometheus metrics

### Logs
```
INFO  [NOTIFICATION_SENT] messageId=uuid, providerMessageId=smtp-123, recipient=user@example.com
WARN  [NOTIFICATION_FAILED] messageId=uuid, error=SMTP timeout, retry=true
INFO  [CONSENT_BLOCKED] messageId=uuid, recipient=user@example.com, reason=OPT_OUT
INFO  [RETRY_SUCCESS] messageId=uuid, attemptCount=2, providerMessageId=smtp-456
ERROR [RETRY_FAILED] messageId=uuid, attemptCount=3, status=FAILED_TERMINAL
```

---

## 🎓 Architecture Decisions

### Why Exponential Backoff?
Prevents thundering herd, gives transient errors time to resolve (network, SMTP server overload).

### Why Fail-Safe on Consent Errors?
GDPR compliance: Better to block and investigate than send without consent.

### Why Message Hash Idempotency?
Prevents duplicate sends due to retries, API replay, or system restarts.

### Why Evidence Artifacts?
DPDP Section 8: Data principals have right to know about their data processing. Evidence provides proof of consent-based communication.

### Why Separate delivery_receipts Table?
Allows multiple status updates from provider (queued → sent → delivered) without overwriting message state.

---

## 📚 References

- **DPDP Act 2023**: Sections 6 (Consent), 8 (Rights of Data Principal), 10 (Audit)
- **GDPR Article 7**: Conditions for consent
- **JavaMail API**: [https://jakarta.ee/specifications/mail/](https://jakarta.ee/specifications/mail/)
- **GreenMail Testing**: [https://greenmail-mail-test.github.io/greenmail/](https://greenmail-mail-test.github.io/greenmail/)
- **Testcontainers**: [https://testcontainers.com/](https://testcontainers.com/)

---

## 🤝 Contributing

### Adding New Provider
1. Implement `NotificationProvider` interface
2. Return `ProviderSendResult` with provider message ID
3. Add `@ConditionalOnProperty` for feature flag
4. Update application.yml with configuration
5. Add integration tests

### Example: Adding Twilio SMS
```java
@Component
@ConditionalOnProperty(prefix = "notification.twilio", name = "enabled")
public class TwilioSmsProvider implements NotificationProvider {
    
    @Override
    public ProviderSendResult send(String recipientAddress, String subject, String body, String format) {
        // Twilio API call
        Message message = Message.creator(
            new PhoneNumber(recipientAddress),
            new PhoneNumber(fromNumber),
            body
        ).create();
        
        return ProviderSendResult.success(
            message.getSid(),
            "TWILIO",
            Map.of("status", message.getStatus().toString())
        );
    }
    
    @Override
    public String getChannel() {
        return "SMS";
    }
}
```

---

## 📄 License

Proprietary - Regulyn Platform © 2026
