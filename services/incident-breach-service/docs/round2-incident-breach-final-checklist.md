# Round 2 Incident Breach – Final Checklist (Part 5)

## Required Configuration

- incident.notifications.receiptWebhookSecret
- incident.notifications.authorityContacts
- incident.notifications.boardContacts
- incident.slaEscalation.dpoEmails
- incident.slaEscalation.adminEmails
- incident.close.requireEvidenceBundle (default: true)
- evidence.service.url
- notification.service.url
- notification.service.stub (set false in non-dev)

## Operational Runbook

### SLA Escalation Scheduler
- Component: IncidentSlaEscalationScheduler
- Frequency: 15 minutes
- Triggers thresholds at 48h and 70h prior to SLA due time
- Idempotent by incident_id + threshold_hours
- Emits INCIDENT_SLA_THRESHOLD_REACHED (audit + outbox)
- Sends DPO/Admin emails via notification-service; marks incident_escalations as NOTIFIED

### SLA Breach Recording
- Component: IncidentOverdueScheduler
- Marks notify_overdue and writes breach metadata
- Emits INCIDENT_SLA_BREACHED (audit + outbox)
- Idempotent via notify_overdue flag

### Evidence Bundle on Close
- Endpoint: POST /incidents/{incidentId}/close
- Builds compliance proof JSON (no raw notice bodies)
- Creates evidence artifact + bundle via evidence-service
- Stores bundle_id on incident_cases
- Emits INCIDENT_EVIDENCE_BUNDLE_CREATED (audit + outbox)
- If requireEvidenceBundle=true, close fails unless bundle is created

## Regulator Proof Mapping

| Requirement | Source of Proof |
|---|---|
| Incident created time | incident_cases.opened_at |
| SLA due time (72h) | incident_cases.notify_due_at |
| Draft snapshot proof | notice_drafts.rendered_sha256 + template_version_id + content_artifact_ref |
| Approval proof | notice_approvals (requested/decided fields) |
| Dispatch proof | notice_dispatch_logs (request/message IDs + timestamps + payload hash) |
| Receipt proof | notice_dispatch_logs.receipt_ref + delivery timestamps |
| SLA escalation (48h/70h) | incident_escalations + INCIDENT_SLA_THRESHOLD_REACHED audit/outbox |
| SLA breach | notify_overdue + INCIDENT_SLA_BREACHED audit/outbox |
| Evidence bundle created | incident_cases.evidence_bundle_id + INCIDENT_EVIDENCE_BUNDLE_CREATED audit/outbox |

## Audit/Outbox Events

- INCIDENT_SLA_THRESHOLD_REACHED
- INCIDENT_SLA_BREACHED
- INCIDENT_EVIDENCE_BUNDLE_CREATED
- incident.closed (existing)

## Verification Checklist

- [ ] Escalation rows are idempotent for each threshold
- [ ] SLA breach recorded once per incident
- [ ] Evidence bundle created on close (or close blocked when required)
- [ ] Receipt ingestion updates dispatch log status
- [ ] Audit + outbox events emitted for all critical actions
