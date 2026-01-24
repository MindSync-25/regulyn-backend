# Event Schemas

Kafka event schema definitions for event-driven communication.

## Schema Format
JSON Schema (Draft 7) or Avro

## Event Categories

### Identity Events
- `IdentityCreated`
- `IdentityUpdated`
- `IdentityDeleted`
- `TenantCreated`

### Consent Events
- `ConsentGranted`
- `ConsentRevoked`
- `ConsentExpired`
- `ConsentUpdated`

### DSAR Events
- `DSARRequestCreated`
- `DSARRequestProcessed`
- `DSARResponseGenerated`
- `DSARRequestCompleted`

### Audit Events
- `AuditLogCreated`
- `ComplianceEventRecorded`

### Data Lifecycle Events
- `DataRetentionPolicyApplied`
- `DataDeletionScheduled`
- `DataDeletionCompleted`

### Incident Events
- `IncidentReported`
- `BreachNotificationSent`

## Schema Structure

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "EventName",
  "type": "object",
  "properties": {
    "eventId": {"type": "string", "format": "uuid"},
    "eventType": {"type": "string"},
    "timestamp": {"type": "string", "format": "date-time"},
    "version": {"type": "string"},
    "source": {"type": "string"},
    "payload": {"type": "object"}
  },
  "required": ["eventId", "eventType", "timestamp", "version", "source", "payload"]
}
```

## Versioning

Events follow semantic versioning (v1, v2, etc.)
- Backward compatible changes: patch version
- New optional fields: minor version
- Breaking changes: major version

## Schema Registry

Use Confluent Schema Registry for:
- Schema validation
- Schema evolution
- Compatibility checks
