# Notification Service

Multi-channel notification and communication preference management service for DPDP-compliant privacy platforms.

## Purpose
Manages notification templates, communication preferences, and multi-channel message delivery with:
- **Template Management**: Versioned templates with multi-language support
- **Preference Management**: Opt-out/opt-in controls per channel and category
- **Multi-Channel Delivery**: EMAIL, SMS, IN_APP, PUSH providers
- **Compliance**: MARKETING messages respect opt-outs, LEGAL/SECURITY messages always delivered
- **Observability**: Full audit trail and outbox event integration

## Tech Stack
- Java 21
- Spring Boot 3
- PostgreSQL (multi-schema: notification + common)
- Kafka (event-driven architecture via lib-events)
- JdbcTemplate (audit_events integration)

## Dependencies
- `lib-common` - AuditWriter for compliance auditing
- `lib-events` - OutboxWriter for transactional event publishing
- `lib-auth` - TenantContext and JWT authentication
- `lib-observability` - Metrics and tracing

---

## Architecture

### Template Lifecycle
```
CREATE → DRAFT → ADD_LANGUAGES → PUBLISH → ACTIVE
                                         ↓
                                      RETIRED (when new version published)
```

- **Template**: Container with `template_key` (e.g., "DSAR_REMINDER")
- **Version**: Numbered versions (1, 2, 3...) with status DRAFT/PUBLISHED/RETIRED
- **Language**: Multi-language variants (en, fr, es) with subject/body templates
- **Publishing**: Only one PUBLISHED version per template; previous auto-retires

### Communication Categories
1. **MARKETING** - Promotional, can be opted out, dispatch blocked if opted out
2. **TRANSACTIONAL** - Service updates, logs opt-out but delivers anyway  
3. **LEGAL** - Legal notices, logs opt-out but always delivers
4. **SECURITY** - Security alerts, logs opt-out but always delivers

### Notification Flow
```
1. Client sends notification request (POST /api/notifications/send)
2. Resolve template by key + language (fallback to default language)
3. Substitute variables: {{user_name}} → "John Doe"
4. Resolve audience: BOARD | ALL_USERS | DATA_PRINCIPAL | USER_IDS
5. Check opt-out preferences per recipient + channel + category
6. If MARKETING + opted_out → SKIP (audit: NOTIFICATION_SKIPPED_OPT_OUT)
7. If LEGAL/SECURITY + opted_out → SEND (audit: NOTIFICATION_SENT + opt-out logged)
8. Calculate message_hash: SHA-256(subject + body)
9. Dispatch via provider: LocalLogProvider (dev), EmailProvider (prod)
10. Record dispatch log with status: SENT | FAILED | SKIPPED_OPT_OUT
```

### Audit & Event Integration
Every operation emits:
- **Audit Event** (via AuditWriter → audit_events table):
  - TEMPLATE_CREATED, TEMPLATE_VERSION_CREATED, TEMPLATE_LANGUAGE_ADDED, TEMPLATE_VERSION_PUBLISHED
  - PREFERENCE_OPTED_OUT, PREFERENCE_OPTED_IN
  - NOTIFICATION_SEND_REQUESTED, NOTIFICATION_SENT, NOTIFICATION_SKIPPED_OPT_OUT, NOTIFICATION_FAILED
- **Outbox Event** (via OutboxWriter → outbox_events table):
  - notification.template_created, notification.template_version_created
  - notification.template_language_added, notification.template_published
  - notification.preference_updated, notification.send_requested

---

## API Endpoints

### 1. Template Management

#### Create Template
```http
POST /api/notifications/templates
Content-Type: application/json

{
  "templateKey": "DSAR_REMINDER",
  "category": "LEGAL",
  "defaultLanguage": "en",
  "description": "DSAR request deadline reminder",
  "isActive": true
}
```

**Response (201 Created):**
```json
{
  "templateId": "uuid",
  "templateKey": "DSAR_REMINDER",
  "category": "LEGAL",
  "defaultLanguage": "en",
  "createdAt": "2026-01-31T15:30:00Z"
}
```

**Validation:**
- `templateKey` unique constraint
- `category` must be: MARKETING | TRANSACTIONAL | LEGAL | SECURITY
- `defaultLanguage` must be ISO 639-1 code (en, fr, es, etc.)

---

#### Create Version
```http
POST /api/notifications/templates/{templateId}/versions
Content-Type: application/json

{
  "versionDescription": "Updated DSAR reminder with extended deadline notice"
}
```

**Response (201 Created):**
```json
{
  "versionId": "uuid",
  "templateId": "uuid",
  "versionNumber": 2,
  "status": "DRAFT",
  "createdAt": "2026-01-31T15:45:00Z"
}
```

**Business Rules:**
- Auto-increments version_number (1, 2, 3...)
- Always starts in DRAFT status
- Cannot create version for non-active template

---

#### Add Language Variant
```http
POST /api/notifications/templates/versions/{versionId}/languages
Content-Type: application/json

{
  "languageCode": "en",
  "subject": "DSAR Request Reminder - {{days_remaining}} Days Left",
  "body": "Dear {{user_name}},\n\nThis is a reminder that your DSAR request (ID: {{request_id}}) is due on {{deadline}}.\n\nThank you.",
  "format": "TEXT"
}
```

**Response (201 Created):**
```json
{
  "languageId": "uuid",
  "versionId": "uuid",
  "languageCode": "en",
  "format": "TEXT",
  "createdAt": "2026-01-31T16:00:00Z"
}
```

**Variable Substitution:**
- Use `{{variable_name}}` syntax
- Substituted at dispatch time from `variables` map
- Missing variables left as {{key}}

**Formats:**
- TEXT - Plain text
- HTML - Rich HTML (future)
- MARKDOWN - Markdown formatting (future)

**Constraints:**
- Cannot add language to PUBLISHED/RETIRED version
- One language variant per (versionId, languageCode)

---

#### Publish Version
```http
POST /api/notifications/templates/versions/{versionId}/publish
Content-Type: application/json

{
  "retireDate": "2026-12-31T23:59:59Z"  // optional
}
```

**Response (200 OK):**
```json
{
  "versionId": "uuid",
  "templateId": "uuid",
  "status": "PUBLISHED",
  "publishedAt": "2026-01-31T16:15:00Z",
  "previousVersionRetired": "uuid-of-v1"
}
```

**Effects:**
1. Sets version status = PUBLISHED
2. Updates template.active_version_id = this versionId
3. Retires previous PUBLISHED version (sets status = RETIRED)
4. Emits audit + outbox events

**Business Rules:**
- Must have at least ONE language variant
- Must have default language variant
- Only one PUBLISHED version per template
- Cannot publish already PUBLISHED version

---

#### Get Active Template
```http
GET /api/notifications/templates/{templateKey}
```

**Response (200 OK):**
```json
{
  "templateId": "uuid",
  "templateKey": "DSAR_REMINDER",
  "category": "LEGAL",
  "activeVersion": {
    "versionId": "uuid",
    "versionNumber": 2,
    "status": "PUBLISHED",
    "languages": [
      {
        "languageCode": "en",
        "subject": "DSAR Request Reminder...",
        "body": "Dear {{user_name}}...",
        "format": "TEXT"
      },
      {
        "languageCode": "fr",
        "subject": "Rappel de demande DSAR...",
        "body": "Cher {{user_name}}...",
        "format": "TEXT"
      }
    ]
  }
}
```

**Use Case:** Fetch template for sending notifications

---

### 2. Preference Management

#### Update Preference (Opt-Out/Opt-In)
```http
POST /api/notifications/preferences
Content-Type: application/json

{
  "dataPrincipalId": "dp-12345",
  "channel": "EMAIL",
  "category": "MARKETING",
  "optedOut": true
}
```

**Response (200 OK):**
```json
{
  "preferenceId": "uuid",
  "dataPrincipalId": "dp-12345",
  "channel": "EMAIL",
  "category": "MARKETING",
  "optedOut": true,
  "updatedAt": "2026-01-31T17:00:00Z"
}
```

**Channels:**
- EMAIL
- SMS
- IN_APP
- PUSH

**Categories:**
- MARKETING - Can be opted out (blocks dispatch)
- TRANSACTIONAL - Can be opted out (logs but sends)
- LEGAL - Can be opted out (logs but sends)
- SECURITY - Can be opted out (logs but sends)

**Upsert Logic:**
- If preference exists for (data_principal_id, channel, category) → UPDATE
- Else → INSERT new preference

---

#### Get Preferences
```http
GET /api/notifications/preferences/{dataPrincipalId}
```

**Response (200 OK):**
```json
{
  "dataPrincipalId": "dp-12345",
  "preferences": [
    {
      "preferenceId": "uuid-1",
      "channel": "EMAIL",
      "category": "MARKETING",
      "optedOut": true
    },
    {
      "preferenceId": "uuid-2",
      "channel": "SMS",
      "category": "TRANSACTIONAL",
      "optedOut": false
    }
  ]
}
```

---

### 3. Notification Dispatch

#### Send Notification
```http
POST /api/notifications/send
Content-Type: application/json

{
  "requestRef": "dsar-reminder-dp12345-2026Q1",  // optional, for idempotency
  "templateKey": "DSAR_REMINDER",
  "language": "en",
  "channel": "EMAIL",
  "audience": {
    "type": "DATA_PRINCIPAL",
    "dataPrincipalId": "dp-12345",
    "userIds": null
  },
  "variables": {
    "user_name": "John Doe",
    "request_id": "DSAR-2026-001",
    "deadline": "2026-02-15",
    "days_remaining": "14"
  }
}
```

**Audience Types:**
1. **BOARD** - All board members (DPDP compliance role)
2. **ALL_USERS** - All active users in tenant
3. **DATA_PRINCIPAL** - Single data principal by ID
4. **USER_IDS** - Explicit list of user IDs

**Response (200 OK):**
```json
{
  "notificationRequestId": "uuid",
  "requestRef": "dsar-reminder-dp12345-2026Q1",
  "totalRecipients": 1,
  "sentCount": 1,
  "skippedCount": 0,
  "failedCount": 0,
  "dispatches": [
    {
      "dispatchId": "uuid",
      "recipientId": "user-uuid",
      "recipientAddress": "john.doe@example.com",
      "status": "SENT",
      "reason": null,
      "sentAt": "2026-01-31T17:30:00Z"
    }
  ]
}
```

**Opt-Out Enforcement:**
```json
// If user opted out of MARKETING EMAIL:
{
  "sentCount": 0,
  "skippedCount": 1,
  "dispatches": [
    {
      "status": "SKIPPED_OPT_OUT",
      "reason": "Opted out from MARKETING notifications on EMAIL channel",
      "sentAt": null
    }
  ]
}
```

**Failed Dispatch:**
```json
{
  "failedCount": 1,
  "dispatches": [
    {
      "status": "FAILED",
      "reason": "Email provider timeout: connection to smtp.example.com failed",
      "sentAt": null
    }
  ]
}
```

**Idempotency:**
- If `requestRef` already exists → throws `IllegalArgumentException`
- Use unique requestRef to prevent duplicate sends

---

## Database Schema

### notification.notification_templates
```sql
CREATE TABLE notification.notification_templates (
    template_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    template_key VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    default_language VARCHAR(10) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    active_version_id UUID REFERENCES notification.notification_template_versions(version_id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    UNIQUE(tenant_id, template_key)
);
CREATE INDEX idx_templates_tenant_key ON notification.notification_templates(tenant_id, template_key);
```

### notification.notification_template_versions
```sql
CREATE TABLE notification.notification_template_versions (
    version_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    template_id UUID NOT NULL REFERENCES notification.notification_templates(template_id),
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- DRAFT, PUBLISHED, RETIRED
    version_description TEXT,
    published_at TIMESTAMP,
    retire_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255),
    UNIQUE(template_id, version_number)
);
```

### notification.notification_template_languages
```sql
CREATE TABLE notification.notification_template_languages (
    language_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    version_id UUID NOT NULL REFERENCES notification.notification_template_versions(version_id),
    language_code VARCHAR(10) NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    format VARCHAR(20) DEFAULT 'TEXT', -- TEXT, HTML, MARKDOWN
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255),
    UNIQUE(version_id, language_code)
);
```

### notification.communication_preferences
```sql
CREATE TABLE notification.communication_preferences (
    preference_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    data_principal_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL, -- EMAIL, SMS, IN_APP, PUSH
    category VARCHAR(50) NOT NULL, -- MARKETING, TRANSACTIONAL, LEGAL, SECURITY
    opted_out BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, data_principal_id, channel, category)
);
CREATE INDEX idx_prefs_principal_channel ON notification.communication_preferences(data_principal_id, channel, category);
```

### notification.notification_requests
```sql
CREATE TABLE notification.notification_requests (
    request_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    request_ref VARCHAR(255),
    template_key VARCHAR(255) NOT NULL,
    language_code VARCHAR(10),
    channel VARCHAR(50) NOT NULL,
    audience_type VARCHAR(50) NOT NULL,
    variables JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255),
    UNIQUE(tenant_id, request_ref)
);
```

### notification.notification_dispatch_logs
```sql
CREATE TABLE notification.notification_dispatch_logs (
    dispatch_id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    request_id UUID NOT NULL REFERENCES notification.notification_requests(request_id),
    recipient_id VARCHAR(255) NOT NULL,
    recipient_address TEXT,
    message_subject TEXT,
    message_body TEXT,
    message_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL, -- SENT, FAILED, SKIPPED_OPT_OUT
    skip_reason TEXT,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_dispatch_request ON notification.notification_dispatch_logs(request_id);
CREATE INDEX idx_dispatch_recipient ON notification.notification_dispatch_logs(recipient_id, status);
```

---

## Configuration

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/regulyn
    username: regulyn
    password: secret
  flyway:
    schemas: notification
    baseline-on-migrate: true
  jpa:
    properties:
      hibernate:
        default_schema: notification
```

### Provider Configuration
Current: **LocalLogProvider** (logs to console)
Future: EmailProvider, SmsProvider, PushProvider

---

## Examples

### Example 1: DSAR Reminder Flow
```bash
# 1. Create template
curl -X POST http://localhost:8080/api/notifications/templates \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "DSAR_REMINDER",
    "category": "LEGAL",
    "defaultLanguage": "en",
    "description": "DSAR request deadline reminder"
  }'

# 2. Create version
curl -X POST http://localhost:8080/api/notifications/templates/{templateId}/versions \
  -H "Content-Type: application/json" \
  -d '{"versionDescription": "Initial version"}'

# 3. Add English language
curl -X POST http://localhost:8080/api/notifications/templates/versions/{versionId}/languages \
  -H "Content-Type: application/json" \
  -d '{
    "languageCode": "en",
    "subject": "DSAR Request Reminder - {{days_remaining}} Days Left",
    "body": "Dear {{user_name}},\n\nYour DSAR request {{request_id}} is due on {{deadline}}.\n\nThank you.",
    "format": "TEXT"
  }'

# 4. Publish version
curl -X POST http://localhost:8080/api/notifications/templates/versions/{versionId}/publish \
  -H "Content-Type: application/json" \
  -d '{}'

# 5. Send notification
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "DSAR_REMINDER",
    "language": "en",
    "channel": "EMAIL",
    "audience": {"type": "DATA_PRINCIPAL", "dataPrincipalId": "dp-12345"},
    "variables": {
      "user_name": "John Doe",
      "request_id": "DSAR-2026-001",
      "deadline": "2026-02-15",
      "days_remaining": "14"
    }
  }'
```

### Example 2: Opt-Out Marketing
```bash
# User opts out of marketing emails
curl -X POST http://localhost:8080/api/notifications/preferences \
  -H "Content-Type: application/json" \
  -d '{
    "dataPrincipalId": "dp-12345",
    "channel": "EMAIL",
    "category": "MARKETING",
    "optedOut": true
  }'

# Attempt to send marketing email → SKIPPED
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "PROMO_OFFER",
    "channel": "EMAIL",
    "audience": {"type": "DATA_PRINCIPAL", "dataPrincipalId": "dp-12345"}
  }'
# Response: {"sentCount": 0, "skippedCount": 1, "dispatches": [{"status": "SKIPPED_OPT_OUT"}]}
```

### Example 3: Security Alert (Always Sent)
```bash
# Even if user opted out of SECURITY emails, message still delivers
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "SECURITY_BREACH_ALERT",
    "channel": "EMAIL",
    "audience": {"type": "ALL_USERS"}
  }'
# Response: {"sentCount": 150, "skippedCount": 0} - All users receive it
# Audit log will note: "User opted out but SECURITY category requires delivery"
```

---

## Event Types

### Audit Events (audit_events table)
1. **TEMPLATE_CREATED** - Template created with key + category
2. **TEMPLATE_VERSION_CREATED** - New version added to template
3. **TEMPLATE_LANGUAGE_ADDED** - Language variant added to version
4. **TEMPLATE_VERSION_PUBLISHED** - Version published, previous retired
5. **PREFERENCE_OPTED_OUT** - User opted out of channel+category
6. **PREFERENCE_OPTED_IN** - User opted in to channel+category
7. **NOTIFICATION_SEND_REQUESTED** - Notification dispatch requested
8. **NOTIFICATION_SENT** - Message successfully dispatched to recipient
9. **NOTIFICATION_SKIPPED_OPT_OUT** - Message skipped due to opt-out (MARKETING only)
10. **NOTIFICATION_FAILED** - Dispatch failed (provider error)

### Outbox Events (outbox_events table)
1. **notification.template_created**
2. **notification.template_version_created**
3. **notification.template_language_added**
4. **notification.template_published**
5. **notification.preference_updated**
6. **notification.send_requested**

---

## Development

### Build
```bash
cd regulyn-backend/services/notification-service
mvn clean install
```

### Run
```bash
mvn spring-boot:run
```

### Test
```bash
mvn test
```

---

## Future Enhancements
1. **Email Provider**: SMTP/SendGrid integration
2. **SMS Provider**: Twilio/AWS SNS integration
3. **Push Provider**: Firebase Cloud Messaging
4. **Retry Logic**: Exponential backoff for failed dispatches
5. **Batch Sending**: Optimize for high-volume (e.g., ALL_USERS audience)
6. **Template Preview**: API to preview with sample variables
7. **Scheduled Notifications**: Delayed/recurring send
8. **Rich Formatting**: HTML + Markdown support
9. **Attachments**: File attachments for email
10. **Analytics**: Open rates, click tracking (with consent)

