package com.regulyn.dsar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "dsar.sla.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DsarSlaScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(DsarSlaScheduler.class);
    
    private final DsarRequestRepository dsarRequestRepository;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    
    public DsarSlaScheduler(
            DsarRequestRepository dsarRequestRepository,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.dsarRequestRepository = dsarRequestRepository;
        this.outboxWriter = outboxWriter;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(cron = "${dsar.sla.scheduler.cron:0 */15 * * * *}")
    @Transactional
    public void checkSlaBreaches() {
        log.info("Running SLA breach check...");
        
        Instant now = Instant.now();
        List<DsarRequestEntity> overdueRequests = dsarRequestRepository.findOverdueDsarRequests(now);
        
        log.info("Found {} overdue DSAR requests", overdueRequests.size());
        
        for (DsarRequestEntity entity : overdueRequests) {
            try {
                entity.setSlaBreached(true);
                dsarRequestRepository.save(entity);
                
                // Write audit event
                writeAudit(entity);
                
                // Write outbox event
                writeOutboxEvent(entity);
                
                log.warn("SLA breached for DSAR {} (tenant: {}, due: {})",
                    entity.getRequestIdPk(), entity.getTenantId(), entity.getDueAt());
                    
            } catch (Exception e) {
                log.error("Failed to mark SLA breach for DSAR {}: {}", 
                    entity.getRequestIdPk(), e.getMessage(), e);
            }
        }
    }
    
    private void writeAudit(DsarRequestEntity entity) {
        try {
            Map<String, Object> payload = Map.of(
                "dsarId", entity.getRequestIdPk().toString(),
                "dueAt", entity.getDueAt().toString(),
                "currentStatus", entity.getStatus()
            );
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = computeHash(payloadJson);
            
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(entity.getTenantId())
                .actorId(null) // System action
                .actorType(AuditEvent.ActorType.SYSTEM)
                .action("dsar.sla_breached")
                .entityType("dsar_request")
                .entityId(entity.getRequestIdPk().toString())
                .payloadHash(payloadHash)
                .build();
            
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            log.error("Failed to write audit event for SLA breach: {}", e.getMessage());
        }
    }
    
    private void writeOutboxEvent(DsarRequestEntity entity) {
        Map<String, Object> payload = Map.of(
            "dsarId", entity.getRequestIdPk().toString(),
            "tenantId", entity.getTenantId().toString(),
            "requestType", entity.getRequestType(),
            "status", entity.getStatus(),
            "dueAt", entity.getDueAt().toString(),
            "breachedAt", Instant.now().toString()
        );
        
        EventEnvelopeV1 event = EventFactory.create(
            "dsar.sla_breached",
            "dsar-grievance-service",
            "dsar_request",
            entity.getRequestIdPk().toString(),
            payload
        );
        
        outboxWriter.write(event);
    }
    
    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
