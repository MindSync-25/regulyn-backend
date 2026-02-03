package com.regulyn.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test configuration providing REAL AuditWriter and simplified OutboxWriter beans.
 * 
 * - AuditWriter: Production class writing to notification.audit_events table
 * - OutboxWriter: Test-friendly subclass writing to notification.outbox_events table via JdbcTemplate
 * 
 * This ensures audit/outbox functionality works in tests WITHOUT requiring OutboxEventRepository.
 */
@TestConfiguration
public class NotificationTestAuditConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationTestAuditConfig.class);
    
    /**
     * Provide real AuditWriter bean from lib-common.
     * Writes to notification.audit_events table.
     */
    @Bean
    @Primary
    public AuditWriter testAuditWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new AuditWriter(jdbcTemplate, objectMapper, "notification-service", "notification");
    }
    
    /**
     * Provide simplified OutboxWriter for testing.
     * Writes directly to notification.outbox_events table via JdbcTemplate.
     */
    @Bean
    @Primary
    public OutboxWriter testOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new OutboxWriter(null, objectMapper) {
            @Override
            public OutboxEvent write(EventEnvelopeV1 envelope) {
                try {
                    // Write to outbox_events table directly (matches V2 migration schema)
                    String sql = """
                        INSERT INTO notification.outbox_events (
                            event_id, tenant_id, event_type, source_service,
                            entity_type, entity_id, correlation_id, occurred_at, payload, payload_hash
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """;
                    
                    String payload = objectMapper.writeValueAsString(envelope.getPayload());
                    
                    jdbcTemplate.update(sql,
                        envelope.getEventId(),
                        envelope.getTenantId(),
                        envelope.getEventType(),
                        envelope.getSourceService(),
                        envelope.getEntityType(),
                        envelope.getEntityId(),
                        envelope.getCorrelationId(),
                        envelope.getOccurredAt(),
                        payload,
                        envelope.getPayloadHash()
                    );
                    
                    logger.debug("Outbox event written: type={}, entityId={}", 
                        envelope.getEventType(), envelope.getEntityId());
                    
                    return null; // Test implementation doesn't return OutboxEvent
                } catch (Exception e) {
                    logger.error("Error writing outbox event: {}", e.getMessage(), e);
                    return null;
                }
            }
        };
    }
}
