package com.regulyn.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Component for writing audit events to the database.
 * Uses JdbcTemplate for direct SQL execution (not JPA) for performance.
 * Automatically uses current tenant from TenantContext.
 */
@Component
public class AuditWriter {
    
    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);
    
    private static final String INSERT_SQL = """
        INSERT INTO incident.audit_events (
            event_id, tenant_id, occurred_at, actor_id, actor_type, 
            service, action, entity_type, entity_id, payload_hash, 
            evidence_id, metadata
        ) VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?, ?, ?, ?::jsonb)
        """;
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    
    public AuditWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }
    
    /**
     * Write an audit event using current tenant from TenantContext.
     */
    public void write(AuditEvent event) {
        try {
            // Convert metadata to PGobject for JSONB
            PGobject metadataJson = new PGobject();
            metadataJson.setType("jsonb");
            if (event.getMetadata() != null) {
                metadataJson.setValue(objectMapper.writeValueAsString(event.getMetadata()));
            } else {
                metadataJson.setValue("{}");
            }
            
            jdbcTemplate.update(INSERT_SQL,
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
            
            log.debug("Audit event written: eventId={}, action={}, entityType={}, entityId={}",
                    event.getEventId(), event.getAction(), event.getEntityType(), event.getEntityId());
                    
        } catch (Exception e) {
            log.error("Failed to write audit event: {}", event.getEventId(), e);
            // Don't throw - audit failures should not break business operations
        }
    }
    
    /**
     * Helper method to create and write an audit event using current tenant context.
     */
    public void auditAction(
            String action,
            String entityType,
            String entityId,
            String payloadHash,
            UUID evidenceId,
            JsonNode metadata) {
        
        UUID currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            log.warn("No tenant context available for audit event: action={}, entityType={}, entityId={}",
                    action, entityType, entityId);
            return;
        }
        
        AuditEvent event = AuditEvent.builder()
                .tenantId(currentTenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(determineActorType())
                .service(serviceName)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payloadHash(payloadHash)
                .evidenceId(evidenceId)
                .metadata(metadata)
                .build();
        
        write(event);
    }
    
    /**
     * Simplified helper for common case without evidence or metadata.
     */
    public void auditAction(
            String action,
            String entityType,
            String entityId,
            String payloadHash) {
        auditAction(action, entityType, entityId, payloadHash, null, null);
    }
    
    private AuditEvent.ActorType determineActorType() {
        // Could be enhanced to check if current auth is JWT vs API key
        UUID userId = TenantContextHolder.getUserId();
        if (userId != null) {
            return AuditEvent.ActorType.USER;
        }
        return AuditEvent.ActorType.SYSTEM;
    }
}
