package com.regulyn.incident.service;

import com.regulyn.incident.client.NotificationSendResult;
import com.regulyn.incident.client.NotificationServiceClient;
import com.regulyn.incident.config.IncidentSlaEscalationProperties;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.entity.IncidentEscalationEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.IncidentCaseRepository;
import com.regulyn.incident.repository.IncidentEscalationRepository;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class IncidentSlaEscalationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IncidentSlaEscalationScheduler.class);
    private static final List<Integer> THRESHOLDS = List.of(48, 70);

    private final IncidentCaseRepository incidentRepository;
    private final IncidentEscalationRepository escalationRepository;
    private final IncidentSlaEscalationProperties escalationProperties;
    private final NotificationServiceClient notificationServiceClient;
    private final AuditOutboxWriter auditOutboxWriter;

    public IncidentSlaEscalationScheduler(IncidentCaseRepository incidentRepository,
                                          IncidentEscalationRepository escalationRepository,
                                          IncidentSlaEscalationProperties escalationProperties,
                                          NotificationServiceClient notificationServiceClient,
                                          AuditOutboxWriter auditOutboxWriter) {
        this.incidentRepository = incidentRepository;
        this.escalationRepository = escalationRepository;
        this.escalationProperties = escalationProperties;
        this.notificationServiceClient = notificationServiceClient;
        this.auditOutboxWriter = auditOutboxWriter;
    }

    @Scheduled(fixedRate = 900000)
    @Transactional
    public void checkSlaEscalations() {
        Instant now = Instant.now();
        List<IncidentCase> incidents = incidentRepository.findOpenIncidentsForEscalation();

        if (incidents.isEmpty()) {
            logger.debug("No incidents eligible for SLA escalation checks");
            return;
        }

        for (IncidentCase incident : incidents) {
            try {
                evaluateThresholds(incident, now);
            } catch (Exception ex) {
                logger.error("Failed SLA escalation check for incident {}", incident.getId(), ex);
            }
        }
    }

    private void evaluateThresholds(IncidentCase incident, Instant now) {
        if (incident.getOpenedAt() == null || incident.getNotifyDueAt() == null) {
            return;
        }

        for (Integer thresholdHours : THRESHOLDS) {
            Instant thresholdAt = incident.getOpenedAt().plusSeconds(thresholdHours * 3600L);
            if (now.isBefore(thresholdAt) || !now.isBefore(incident.getNotifyDueAt())) {
                continue;
            }

            IncidentEscalationEntity escalation = createEscalationIfAbsent(incident, thresholdHours);
            if (escalation == null) {
                continue;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("tenant_id", incident.getTenantId().toString());
            payload.put("incident_id", incident.getId().toString());
            payload.put("threshold_hours", thresholdHours);
            payload.put("created_at", incident.getOpenedAt() != null ? incident.getOpenedAt().toString() : null);
            payload.put("sla_due_at", incident.getNotifyDueAt().toString());
            payload.put("occurred_at", now.toString());

                runWithTenantContext(incident.getTenantId(), () ->
                auditOutboxWriter.publish(
                    "INCIDENT_SLA_THRESHOLD_REACHED",
                    "incident_escalation",
                    escalation.getId().toString(),
                    incident.getTenantId(),
                    null,
                    payload
                )
                );

            notifyEscalation(incident, escalation, thresholdHours, now);
        }
    }

    private IncidentEscalationEntity createEscalationIfAbsent(IncidentCase incident, int thresholdHours) {
        if (escalationRepository.existsByIncidentIdAndThresholdHours(incident.getId(), thresholdHours)) {
            return null;
        }

        IncidentEscalationEntity escalation = new IncidentEscalationEntity();
        escalation.setTenantId(incident.getTenantId());
        escalation.setIncidentId(incident.getId());
        escalation.setThresholdHours(thresholdHours);
        escalation.setStatus("CREATED");

        try {
            return escalationRepository.save(escalation);
        } catch (DataIntegrityViolationException ex) {
            return null;
        }
    }

    private void runWithTenantContext(UUID tenantId, Runnable task) {
        TenantContext previous = TenantContextHolder.getContext();
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setRequestId(UUID.randomUUID().toString());
        context.setTraceId(UUID.randomUUID().toString());

        try {
            TenantContextHolder.setContext(context);
            task.run();
        } finally {
            if (previous != null) {
                TenantContextHolder.setContext(previous);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private void notifyEscalation(IncidentCase incident,
                                  IncidentEscalationEntity escalation,
                                  int thresholdHours,
                                  Instant now) {
        List<String> recipients = new ArrayList<>();
        if (escalationProperties.getDpoEmails() != null) {
            recipients.addAll(escalationProperties.getDpoEmails());
        }
        if (escalationProperties.getAdminEmails() != null) {
            recipients.addAll(escalationProperties.getAdminEmails());
        }
        recipients = recipients.stream()
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            logger.warn("No escalation recipients configured for incident {}", incident.getId());
            return;
        }

        String subject = "SLA Escalation " + thresholdHours + "h - Incident " + incident.getId();
        String body = "Incident " + incident.getId() + " reached SLA threshold " + thresholdHours
                + "h. Due at: " + incident.getNotifyDueAt() + ". Current time: " + now + ".";

        String notificationRequestId = null;
        boolean anySent = false;

        for (String recipient : recipients) {
            try {
                NotificationSendResult result = notificationServiceClient.sendEmail(
                        incident.getTenantId(),
                        recipient,
                        subject,
                        body,
                        null,
                        null
                );

                if (result.status() != null && "SENT".equals(result.status())) {
                    anySent = true;
                    if (notificationRequestId == null) {
                        notificationRequestId = result.notificationRequestId();
                    }
                }
            } catch (RuntimeException ex) {
                logger.warn("SLA escalation notification failed for incident {} to {}: {}",
                        incident.getId(), recipient, ex.getMessage());
            }
        }

        if (anySent) {
            escalation.setStatus("NOTIFIED");
            escalation.setNotificationRequestId(notificationRequestId);
            escalation.setNotifiedAt(Instant.now());
            escalationRepository.save(escalation);
        }
    }
}
