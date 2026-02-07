# Notification Service

Multi-channel notification and communication preference management service for DPDP-compliant privacy platforms.

## Purpose
Manages notification templates, communication preferences, and multi-channel message delivery with:
- **Template Management**: Versioned templates with multi-language support
- **Preference Management**: Opt-out/opt-in controls per channel and category
- **Multi-Channel Delivery**: EMAIL via SMTP (plus LocalLogProvider for dev); other channels are accepted as strings but not yet implemented
- **Compliance**: MARKETING opt-outs block sends; LEGAL consent-check failures can be bypassed with audit trails (delivery still depends on provider)
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
Categories are free-form strings. Current consent rules are implemented for `MARKETING` and `LEGAL` only; other categories are treated as non-marketing (opt-out does not block, consent-check failures allow send with audit, and no LEGAL bypass event is emitted).

Recommended categories (from DB comments):
1. **MARKETING**
2. **LEGAL**
3. **SECURITY** (treated as non-marketing unless explicit logic is added)
4. **OPERATIONS** (treated as non-marketing unless explicit logic is added)

### Notification Flow
```
1. Client sends notification request (POST /api/notifications/send)
2. Resolve template by key + language (fallback to default language)
3. Substitute variables: {{user_name}} → "John Doe"
4. Resolve audience: BOARD | ALL_USERS | DATA_PRINCIPAL | USER_IDS
5. Create notification_message record (idempotent hash)
6. Apply consent enforcement (local opt-out + external consent check)
7. If MARKETING + opted_out → CONSENT_BLOCKED (audit/outbox: NOTIFICATION_CONSENT_BLOCKED)
8. If consent check fails:
  - MARKETING → CONSENT_BLOCKED (audit/outbox: NOTIFICATION_CONSENT_CHECK_FAILED)
  - LEGAL → send allowed with bypass audit/outbox (NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL)
  - Other categories → send allowed with NOTIFICATION_CONSENT_CHECK_FAILED
9. Non-marketing opt-outs are logged but do not block send
10. Calculate message_hash: SHA-256(requestId|recipientAddress|templateIdOrKey|category)
11. Dispatch via provider: LocalLogProvider (dev) or SmtpEmailProvider (when SMTP enabled)
12. Record dispatch log for legacy/compat reporting (may show SKIPPED_OPT_OUT while the message status is CONSENT_BLOCKED)
```

### Audit & Event Integration
Every operation emits:
- **Audit Event** (via AuditWriter → audit_events table):
  - TEMPLATE_CREATED, TEMPLATE_VERSION_CREATED, TEMPLATE_LANGUAGE_ADDED, TEMPLATE_VERSION_PUBLISHED
  - PREFERENCE_OPTED_OUT, PREFERENCE_OPTED_IN
  - NOTIFICATION_SEND_REQUESTED, NOTIFICATION_SENT, NOTIFICATION_FAILED
  - NOTIFICATION_CONSENT_BLOCKED, NOTIFICATION_CONSENT_CHECK_FAILED, NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL
- **Outbox Event** (via OutboxWriter → outbox_events table):
  - notification.template_created, notification.template_version_created
  - notification.template_language_added, notification.template_published
  - notification.preference_updated, notification.send_requested

---

## Consent Enforcement (Round 2 Part 5)

### Rules by Category
- **MARKETING**: Fail-closed. Local opt-out blocks immediately. External consent check failures also block.
- **LEGAL**: External consent check failures are allowed **only** with explicit bypass events.
- **Other categories**: Consent check failures do **not** block; send is allowed but must be recorded.

### Required Events
- `NOTIFICATION_CONSENT_BLOCKED`
- `NOTIFICATION_CONSENT_CHECK_FAILED`
- `NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL`

### Statuses
- Message status: `CONSENT_BLOCKED`
- Dispatch log status: `SKIPPED_OPT_OUT`

### Operational Notes
- Consent-service outage blocks MARKETING only.
- LEGAL and other non-marketing categories continue but emit `NOTIFICATION_CONSENT_CHECK_FAILED`.
- Use audit/outbox + evidence references to prove consent decision trails.

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
- `category` is a free-form string; consent rules are enforced only for MARKETING and LEGAL
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
- EMAIL (implemented)
- Other channels are accepted as strings but have no provider implementation yet

**Categories:**
- MARKETING - Can be opted out (blocks dispatch)
- LEGAL - Can be opted out (logs but sends)
- Other categories are treated as non-marketing (opt-out does not block)

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
      "status": "CONSENT_BLOCKED",
      "reason": "OPTED_OUT",
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
- If `requestRef` already exists → returns the existing request response and emits `NOTIFICATION_IDEMPOTENT_REPLAY`
- Use a unique requestRef to prevent duplicate sends

---

## Database Schema

Schema is defined in Flyway migrations. See:
- [services/notification-service/src/main/resources/db/migration/V4__notification_domain.sql](services/notification-service/src/main/resources/db/migration/V4__notification_domain.sql)
- [services/notification-service/src/main/resources/db/migration/V5__notification_messages_and_delivery_receipts.sql](services/notification-service/src/main/resources/db/migration/V5__notification_messages_and_delivery_receipts.sql)

Key tables:
- `notification.notification_templates`
- `notification.notification_template_versions`
- `notification.notification_template_language`
- `notification.communication_preferences`
- `notification.notification_requests`
- `notification.notification_dispatch_logs`
- `notification.notification_messages`
- `notification.notification_delivery_receipts`

Note: tenant_id types differ across tables (V4 uses VARCHAR(100); V5 uses UUID). Use the migrations as the source of truth.

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

server:
  port: 8092

consent:
  enabled: true
  baseUrl: http://localhost:8084
  timeout: 2s

notification:
  retry:
    enabled: true
    fixedDelayMs: 30000
    batchSize: 50

evidence:
  service:
    url: http://localhost:8083
```

### Provider Configuration
Current: **LocalLogProvider** (dev) and **SmtpEmailProvider** when `smtp.enabled=true`
Future: SMS/WhatsApp, Push providers

---

## Examples

### Example 1: DSAR Reminder Flow
```bash
# 1. Create template
curl -X POST http://localhost:8092/api/notifications/templates \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "DSAR_REMINDER",
    "category": "LEGAL",
    "defaultLanguage": "en",
    "description": "DSAR request deadline reminder"
  }'

# 2. Create version
curl -X POST http://localhost:8092/api/notifications/templates/{templateId}/versions \
  -H "Content-Type: application/json" \
  -d '{"versionDescription": "Initial version"}'

# 3. Add English language
curl -X POST http://localhost:8092/api/notifications/templates/versions/{versionId}/languages \
  -H "Content-Type: application/json" \
  -d '{
    "languageCode": "en",
    "subject": "DSAR Request Reminder - {{days_remaining}} Days Left",
    "body": "Dear {{user_name}},\n\nYour DSAR request {{request_id}} is due on {{deadline}}.\n\nThank you.",
    "format": "TEXT"
  }'

# 4. Publish version
curl -X POST http://localhost:8092/api/notifications/templates/versions/{versionId}/publish \
  -H "Content-Type: application/json" \
  -d '{}'

# 5. Send notification
curl -X POST http://localhost:8092/api/notifications/send \
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
curl -X POST http://localhost:8092/api/notifications/preferences \
  -H "Content-Type: application/json" \
  -d '{
    "dataPrincipalId": "dp-12345",
    "channel": "EMAIL",
    "category": "MARKETING",
    "optedOut": true
  }'

# Attempt to send marketing email → CONSENT_BLOCKED
curl -X POST http://localhost:8092/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "PROMO_OFFER",
    "channel": "EMAIL",
    "audience": {"type": "DATA_PRINCIPAL", "dataPrincipalId": "dp-12345"}
  }'
# Response: {"sentCount": 0, "skippedCount": 1, "dispatches": [{"status": "CONSENT_BLOCKED"}]}
```

### Example 3: Non-marketing Alert (Consent Does Not Block)
```bash
# Non-marketing categories do not block on opt-out; delivery still depends on provider availability
curl -X POST http://localhost:8092/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "templateKey": "SECURITY_BREACH_ALERT",
    "channel": "EMAIL",
    "audience": {"type": "ALL_USERS"}
  }'
# Response: {"sentCount": 150, "skippedCount": 0} - All users receive it (subject to provider success)
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
9. **NOTIFICATION_CONSENT_BLOCKED** - Message blocked by consent rules
10. **NOTIFICATION_CONSENT_CHECK_FAILED** - Consent service failed (audit trail)
11. **NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL** - Legal bypass on consent failure
12. **NOTIFICATION_FAILED** - Dispatch failed (provider error)

### Outbox Events (outbox_events table)
1. **notification.template_created**
2. **notification.template_version_created**
3. **notification.template_language_added**
4. **notification.template_published**
5. **notification.preference_updated**
6. **notification.send_requested**
7. **NOTIFICATION_CONSENT_BLOCKED**
8. **NOTIFICATION_CONSENT_CHECK_FAILED**
9. **NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL**

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
1. **SMS Provider**: Twilio/AWS SNS integration
2. **Push Provider**: Firebase Cloud Messaging
3. **Batch Sending**: Optimize for high-volume (e.g., ALL_USERS audience)
4. **Template Preview**: API to preview with sample variables
5. **Scheduled Notifications**: Delayed/recurring send
6. **Rich Formatting**: HTML + Markdown support
7. **Attachments**: File attachments for email
8. **Analytics**: Open rates, click tracking (with consent)

