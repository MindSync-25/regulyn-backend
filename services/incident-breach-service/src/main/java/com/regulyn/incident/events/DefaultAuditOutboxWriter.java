package com.regulyn.incident.events;

import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultAuditOutboxWriter implements AuditOutboxWriter {

    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final String serviceName;

    public DefaultAuditOutboxWriter(
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            @Value("${spring.application.name:incident-breach-service}") String serviceName) {
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.serviceName = serviceName;
    }

    @Override
    public void publish(String eventType, String entityType, String entityId, UUID tenantId, UUID actorId, Object payload) {
        String canonicalJson = EventJson.toCanonicalJson(payload);
        String payloadHash = EventHasher.sha256(canonicalJson);

        AuditEvent.ActorType auditActorType = actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM;
        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .actorType(auditActorType)
                .service(serviceName)
                .action(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .payloadHash(payloadHash)
                .build();
        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = EventFactory.create(
                eventType,
                serviceName,
                entityType,
                entityId,
                payload
        );
        envelope.setActorType(actorId != null ? ActorType.USER : ActorType.SYSTEM);
        outboxWriter.write(envelope);
    }
}
