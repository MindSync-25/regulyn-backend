package com.regulyn.incident.events;

import java.util.UUID;

public interface AuditOutboxWriter {
    void publish(String eventType,
                 String entityType,
                 String entityId,
                 UUID tenantId,
                 UUID actorId,
                 Object payload);
}
