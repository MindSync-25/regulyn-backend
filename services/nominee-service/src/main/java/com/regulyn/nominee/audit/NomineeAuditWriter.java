package com.regulyn.nominee.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class NomineeAuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String insertSql;
    private final String serviceName;

    public NomineeAuditWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:nominee-service}") String serviceName,
            @Value("${audit.schema:nominee}") String auditSchema) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        this.insertSql = String.format("""
            INSERT INTO %s.audit_events (
                event_id, tenant_id, occurred_at, actor_id, actor_type,
                service, action, entity_type, entity_id, payload_hash,
                evidence_id, metadata
            ) VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?::text, ?, ?, ?::jsonb)
            """, auditSchema);
    }

    public void writeOrThrow(AuditEvent event) {
        try {
            PGobject metadataJson = new PGobject();
            metadataJson.setType("jsonb");
            if (event.getMetadata() != null) {
                metadataJson.setValue(objectMapper.writeValueAsString(event.getMetadata()));
            } else {
                metadataJson.setValue("{}");
            }

            int updated = jdbcTemplate.update(insertSql,
                    event.getEventId(),
                    event.getTenantId(),
                    Timestamp.from(event.getTimestamp()),
                    event.getActorId(),
                    event.getActorType().name(),
                    event.getService() != null ? event.getService() : serviceName,
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getPayloadHash(),
                    event.getEvidenceId(),
                    metadataJson
            );

            if (updated != 1) {
                throw new IllegalStateException("Failed to write audit event: " + event.getEventId());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write audit event", e);
        }
    }
}
