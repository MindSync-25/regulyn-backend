# Event Model and Outbox Pattern

## Overview

Regulyn uses an event-driven architecture with the Outbox Pattern to ensure reliable event publishing across all services. This document describes the Event Envelope v1 specification and the outbox implementation.

## Event Envelope v1

The Event Envelope v1 is a standardized structure for all domain events in the Regulyn platform.

### Schema

```java
{
  "eventId": "UUID",           // Unique identifier for this event
  "eventType": "string",       // Event type (e.g., "consent.created", "dsar.created")
  "tenantId": "UUID",          // Tenant identifier (from TenantContext)
  "actorId": "UUID",           // User/API Key that triggered the event (nullable)
  "actorType": "enum",         // USER, API_KEY, or SYSTEM
  "sourceService": "string",   // Service that produced the event (e.g., "consent-service")
  "entityType": "string",      // Entity type (e.g., "CONSENT", "DSAR", "EVIDENCE")
  "entityId": "string",        // Unique identifier for the entity
  "occurredAt": "Instant",     // When the event occurred (ISO-8601 timestamp)
  "correlationId": "string",   // Request ID / Trace ID for distributed tracing
  "idempotencyKey": "string",  // Optional idempotency key
  "payload": "JsonNode",       // Event-specific data
  "payloadHash": "string",     // SHA-256 hash of canonical payload JSON
  "schemaVersion": 1,          // Version of the envelope schema
  "metadata": "Map"            // Additional metadata
}
```

### Event Types

Event types follow the pattern: `<domain>.<action>`

Examples:
- `consent.created` - Consent record created
- `consent.updated` - Consent record updated
- `consent.withdrawn` - Consent withdrawn
- `dsar.created` - DSAR request submitted
- `dsar.completed` - DSAR request completed
- `evidence.created` - Evidence record created

### Payload Hashing

All event payloads are hashed using SHA-256 to ensure integrity. The payload is first serialized to canonical JSON (with stable key ordering) before hashing.

```java
// Example: Creating an event
Map<String, Object> payload = new HashMap<>();
payload.put("receiptId", receiptId);
payload.put("purpose", "marketing");

EventEnvelopeV1 envelope = EventFactory.create(
    "consent.created",
    "consent-service",
    "CONSENT",
    receiptId,
    payload
);
```

## Outbox Pattern

The Outbox Pattern ensures that domain changes and event publishing happen atomically within the same database transaction.

### How It Works

1. **Write Phase**: When a domain entity is created/updated, the service:
   - Persists the domain entity to its table
   - Writes an event to the `outbox_events` table
   - Both operations happen in the same transaction

2. **Publish Phase** (Future - when Kafka is integrated):
   - A background relay process reads pending events from the outbox
   - Publishes events to Kafka
   - Marks events as PUBLISHED or FAILED

### Outbox Table Schema

Each service has its own `outbox_events` table in its schema:

```sql
CREATE TABLE outbox_events (
    outbox_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    source_service TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    payload_hash TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Event Lifecycle

```
PENDING → PUBLISHED (success)
        → FAILED (after retry exhaustion)
```

- **PENDING**: Event written to outbox, waiting to be published
- **PUBLISHED**: Event successfully sent to Kafka
- **FAILED**: Publishing failed after all retry attempts

### Retry Strategy

When Kafka publishing is enabled:
- Failed events are retried with exponential backoff
- Maximum retry attempts: configurable (default: 5)
- Backoff: 1min, 5min, 15min, 1hr, 24hr

### Implementation

#### 1. EventFactory

Creates event envelopes with automatic context population:

```java
EventEnvelopeV1 envelope = EventFactory.create(
    "consent.created",      // event type
    "consent-service",      // source service
    "CONSENT",              // entity type
    receiptId,              // entity ID
    payloadData             // event payload
);
```

The factory automatically:
- Generates event ID
- Extracts tenantId and userId from TenantContext
- Gets correlationId from MDC
- Computes payload hash
- Sets occurredAt timestamp

#### 2. OutboxWriter

Persists events to the outbox table:

```java
@Transactional
public ConsentResponse createConsent(ConsentRequest request) {
    // 1. Persist domain entity
    consentRepository.save(consentRecord);
    
    // 2. Create event
    EventEnvelopeV1 envelope = EventFactory.create(...);
    
    // 3. Write to outbox (same transaction)
    outboxWriter.write(envelope);
    
    return response;
}
```

#### 3. OutboxPublisher (Interface)

```java
public interface OutboxPublisher {
    int publishPendingBatch(int maxEvents);
}
```

Current implementation: `DefaultNoopOutboxPublisher` (does nothing until Kafka is integrated)

#### 4. OutboxRelayService

Background service that will publish events (disabled by default):

```yaml
regulyn:
  outbox:
    relay:
      enabled: false  # Set to true when Kafka is ready
```

## Services with Outbox Integration

The following services currently integrate with the outbox pattern:

1. **consent-service**: Publishes `consent.created` events
2. **dsar-grievance-service**: Publishes `dsar.created` events
3. **evidence-reporting-service**: Publishes `evidence.created` events

## Future: Kafka Integration

When Kafka is integrated:

1. Deploy Kafka cluster
2. Implement `KafkaOutboxPublisher` 
3. Configure topic mappings
4. Enable relay service: `regulyn.outbox.relay.enabled=true`
5. Deploy relay as scheduled job or separate process

### Topic Naming Convention

Topics will follow the pattern: `regulyn.<domain>.<action>`

Examples:
- `regulyn.consent.created`
- `regulyn.dsar.created`
- `regulyn.evidence.created`

### Consumer Groups

Each downstream service will have its own consumer group to independently consume events.

## Testing

### Unit Tests

Test outbox writes in service tests:

```java
@Test
void createConsent_shouldWriteToOutbox() {
    ConsentResponse response = consentService.createConsent(request);
    
    List<OutboxEvent> events = outboxRepository.findAll();
    assertEquals(1, events.size());
    assertEquals("consent.created", events.get(0).getEventType());
}
```

### Integration Tests

Use Testcontainers for full integration testing with PostgreSQL.

## Monitoring

When Kafka is integrated, monitor:
- Outbox table size (should not grow unbounded)
- Event lag (time between event creation and publishing)
- Failed events count
- Retry attempts

## Best Practices

1. **Keep payloads small**: Only include necessary data
2. **Hash sensitive data**: Use hashes instead of raw PII in event payloads
3. **Use correlation IDs**: Always propagate correlation IDs for tracing
4. **Idempotency keys**: Use for operations that must not be duplicated
5. **Schema evolution**: Increment `schemaVersion` when envelope structure changes

## Troubleshooting

### Events stuck in PENDING state

- Check if relay service is enabled
- Verify Kafka connectivity
- Check outbox table for errors in `last_error` column

### Duplicate events

- Ensure consumers are idempotent
- Check idempotency keys are being used correctly

### Payload hash mismatches

- Verify canonical JSON serialization
- Check for floating point precision issues
- Ensure stable key ordering in maps
