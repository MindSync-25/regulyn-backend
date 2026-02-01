package com.regulyn.incident.service;

import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.repository.IncidentCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class IncidentOverdueScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(IncidentOverdueScheduler.class);
    
    private final IncidentCaseRepository incidentRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public IncidentOverdueScheduler(
            IncidentCaseRepository incidentRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.incidentRepository = incidentRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void checkOverdueIncidents() {
        Instant now = Instant.now();
        List<IncidentCase> overdueIncidents = incidentRepository.findOverdueIncidents(now);
        
        if (overdueIncidents.isEmpty()) {
            logger.debug("No overdue incidents found");
            return;
        }
        
        logger.info("Found {} overdue incidents", overdueIncidents.size());
        
        for (IncidentCase incident : overdueIncidents) {
            try {
                incident.setNotifyOverdue(true);
                incidentRepository.save(incident);
                
                writeAudit(incident.getTenantId(), incident.getId());
                writeOutbox(incident.getTenantId(), incident.getId(), incident.getSeverity(), incident.getStatus());
                
                logger.warn("Marked incident {} as overdue (due: {}, status: {})",
                    incident.getId(), incident.getNotifyDueAt(), incident.getStatus());
                    
            } catch (Exception e) {
                logger.error("Failed to process overdue incident {}", incident.getId(), e);
            }
        }
    }
    
    private void writeAudit(java.util.UUID tenantId, java.util.UUID incidentId) {
        try {
            String hash = hashPayload(incidentId.toString());
            
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(null)
                .actorType(AuditEvent.ActorType.SYSTEM)
                .service("incident-breach-service")
                .action("incident.notify_overdue")
                .entityType("IncidentCase")
                .entityId(incidentId.toString())
                .payloadHash(hash)
                .build();
            
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }
    
    private void writeOutbox(java.util.UUID tenantId, java.util.UUID incidentId, String severity, String status) {
        try {
            EventEnvelopeV1 event = EventFactory.create(
                "incident.notify_overdue",
                "incident-breach-service",
                "incident_case",
                incidentId.toString(),
                Map.of(
                    "incidentId", incidentId.toString(),
                    "tenantId", tenantId.toString(),
                    "severity", severity,
                    "status", status
                )
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox", e);
        }
    }
    
    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}

