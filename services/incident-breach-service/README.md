# Incident & Breach Service

Security incident and data breach management service. This service covers the full incident lifecycle, notification workflows, evidence/compliance proofing, and operational hardening across Round 1 and Round 2.

## Scope & Purpose
- Record and track security incidents end-to-end
- Manage incident status transitions and tasks
- Draft/approve/send breach notifications
- Track delivery receipts and audit evidence
- Generate compliance proof + evidence bundles on close
- Enforce SLA escalation and overdue breach handling

## Tech Stack
- Java 21
- Spring Boot 3.3.x
- PostgreSQL (schema: `incident` + `outbox`)
- Flyway for migrations
- WireMock + Testcontainers (E2E tests)

## Dependencies
- lib-common
- lib-events
- lib-temporal
- lib-observability
- lib-auth

## Service Architecture (High Level)
- REST API for incident CRUD, transitions, notifications, and close
- JPA repositories for incident domain objects
- Audit + Outbox event emission for critical actions
- Scheduled jobs for SLA escalation + overdue breach marking
- External integrations: Notification Service, Evidence Service

## Round 1 Features (Baseline)
### Incident Management
- Create incidents with severity, summary, metadata
- Status transitions: `OPENED → TRIAGED → INVESTIGATING → NOTIFIED → CONTAINED → CLOSED`
- Incident details and list API

### Tasks
- Create incident tasks with type, status, assignee, notes

### Notifications (Round 1)
- Draft notification content
- Approve notification
- Send notification via Notification Service
- Persist dispatch info in incident notification record

## Round 2 Features (Hardening)
### Round 2 Notice Templates & Drafts
- Templates and versions (immutable versions)
- Draft creation from templates with rendered SHA-256
- Maker-checker approval workflow
- Draft approval/rejection audit trail

### Dispatch Logs + Receipts
- Dispatch logs per recipient/channel with SHA-256 payload hash
- Idempotent dispatch queue creation
- Send queued dispatch logs
- Receipt ingestion with webhook auth and status updates

### SLA Escalation Scheduler
- Scheduled checks at 48h and 70h thresholds
- Idempotent escalation creation (`incident_id`, `threshold_hours`)
- Audit + Outbox: `INCIDENT_SLA_THRESHOLD_REACHED`
- Notification to DPO/Admin emails

### Overdue/SLA Breach Scheduler
- Marks incidents overdue when due time passes
- Writes breach metadata into incident
- Audit + Outbox: `INCIDENT_SLA_BREACHED`
- Idempotent breach emission (no event spam)

### Close-Time Evidence Compliance
- Builds compliance proof JSON (incident, SLA, drafts, approvals, dispatches, receipts)
- Proof SHA-256 hashing
- Evidence artifact creation + bundle creation in Evidence Service
- Persists `evidence_bundle_id` and emits `INCIDENT_EVIDENCE_BUNDLE_CREATED`
- Close gating when `incident.close.requireEvidenceBundle=true`:
	- Block close if any draft is `DRAFT` or `APPROVAL_PENDING`
	- Require dispatch logs for `APPROVED`/`DISPATCHED` drafts
	- Allow `REJECTED` drafts without dispatch logs

## API Endpoints (Core)
### Incidents
- `POST /incidents` Create incident
- `GET /incidents` List incidents
- `GET /incidents/{incidentId}` Incident details
- `POST /incidents/{incidentId}/transition` Transition status
- `POST /incidents/{incidentId}/close` Close incident

### Tasks
- `POST /incidents/{incidentId}/tasks` Create task

### Notifications
- `POST /incidents/{incidentId}/notifications/draft` Draft notification
- `POST /incidents/{incidentId}/notifications/{notificationId}/approve` Approve notification
- `POST /incidents/{incidentId}/notifications/{notificationId}/reject` Reject notification
- `POST /incidents/{incidentId}/notifications/{notificationId}/send` Send notification

### Receipts
- `POST /incidents/notifications/receipts` Receipt webhook ingestion

## Key Domain Entities (Tables)
- `incident_cases`
- `incident_status_history`
- `incident_tasks`
- `incident_notifications`
- `notice_templates`
- `notice_template_versions`
- `notice_drafts`
- `notice_approvals`
- `notice_dispatch_logs`
- `incident_escalations`
- `audit_events`
- `outbox_events`

## Events (Audit/Outbox)
### Incident lifecycle
- `incident.created`
- `incident.status_changed`
- `incident.closed`

### Round 2 notice workflow
- `NOTICE_DRAFT_CREATED`
- `NOTICE_APPROVAL_REQUESTED`
- `NOTICE_APPROVED`
- `NOTICE_REJECTED`
- `NOTICE_DISPATCH_QUEUED`
- `NOTICE_DISPATCH_SENT`

### SLA escalation / breach
- `INCIDENT_SLA_THRESHOLD_REACHED`
- `INCIDENT_SLA_BREACHED`

### Evidence
- `INCIDENT_EVIDENCE_BUNDLE_CREATED`

## Configuration
### Core
- `incident.round2.notices.bridgeEnabled` (default true)
- `incident.round2.notices.makerCheckerEnabled` (default true)
- `incident.close.requireEvidenceBundle` (default true)

### SLA escalation
- `incident.sla-escalation.dpoEmails[]`
- `incident.sla-escalation.adminEmails[]`

### Notification service
- `notification.service.url`
- `notification.service.stub`
- `incident.notifications.receiptWebhookSecret`
- `incident.notifications.authorityContacts[]`
- `incident.notifications.boardContacts[]`

### Evidence service
- `evidence.service.url`

## Security & Tenant Context
- Tenant isolation enforced by `X-Tenant-ID`
- Actor identity via `X-Actor-ID`
- Role via `X-Actor-Role` (TENANT_ADMIN, DPO, REVIEWER)
- Receipt webhook requires `X-Webhook-Secret`

## Schedulers
- `IncidentSlaEscalationScheduler` (every 15 min)
- `IncidentOverdueScheduler` (periodic overdue marking)

## Evidence Proof Contents (Close)
The compliance proof JSON includes:
- Incident ID, tenant ID, opened time, SLA due time
- Status history transitions
- Tasks list
- Per-draft proof: template version, rendered SHA-256, approval info
- Dispatch logs: payload SHA-256, request/provider IDs, delivery timestamps, receipt ref
- Missing/completeness reasons

## Testing
- Unit tests for service logic
- E2E Testcontainers workflows with WireMock stubs
- Round 2 E2E covers escalation idempotency, send/receipt/close, evidence gating

## How to Run (Local)
1) Configure environment variables or override application.yml
2) Run via Maven: `mvn -f services/incident-breach-service/pom.xml spring-boot:run`

## How to Test
- `mvn -f services/incident-breach-service/pom.xml test`

## Related Docs
- [docs/round2-incident-breach-final-checklist.md](docs/round2-incident-breach-final-checklist.md)
- [docs/api/README.md](docs/api/README.md)

## Notes
- All critical actions emit both audit and outbox events for compliance traceability.
- Evidence bundling is enforced when `incident.close.requireEvidenceBundle=true`.
