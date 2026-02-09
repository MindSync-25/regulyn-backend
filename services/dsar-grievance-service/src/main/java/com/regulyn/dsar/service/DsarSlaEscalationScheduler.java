package com.regulyn.dsar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.client.NotificationSendResult;
import com.regulyn.dsar.client.NotificationServiceClient;
import com.regulyn.dsar.config.DsarSlaEscalationProperties;
import com.regulyn.dsar.entity.DsarEscalationTaskEntity;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.entity.EscalationStatus;
import com.regulyn.dsar.entity.EscalationThreshold;
import com.regulyn.dsar.repository.DsarEscalationTaskRepository;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Component
@ConditionalOnProperty(value = "dsar.sla.escalation.enabled", havingValue = "true", matchIfMissing = true)
public class DsarSlaEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DsarSlaEscalationScheduler.class);

    private final DsarRequestRepository dsarRequestRepository;
    private final DsarEscalationTaskRepository escalationTaskRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final DsarSlaEscalationProperties properties;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public DsarSlaEscalationScheduler(
        DsarRequestRepository dsarRequestRepository,
        DsarEscalationTaskRepository escalationTaskRepository,
        NotificationServiceClient notificationServiceClient,
        DsarSlaEscalationProperties properties,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper
    ) {
        this.dsarRequestRepository = dsarRequestRepository;
        this.escalationTaskRepository = escalationTaskRepository;
        this.notificationServiceClient = notificationServiceClient;
        this.properties = properties;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${dsar.sla.escalation.cron:0 */15 * * * *}")
    @Transactional
    public void checkSlaEscalations() {
        Instant now = Instant.now();

        List<ThresholdConfig> thresholds = List.of(
            new ThresholdConfig(EscalationThreshold.D60, properties.getThresholds().getWarningDays(), "WARNING"),
            new ThresholdConfig(EscalationThreshold.D80, properties.getThresholds().getCriticalDays(), "CRITICAL"),
            new ThresholdConfig(EscalationThreshold.D90, properties.getThresholds().getBreachDays(), "BREACH")
        );

        for (ThresholdConfig thresholdConfig : thresholds) {
            if (thresholdConfig.days == null || thresholdConfig.days <= 0) {
                continue;
            }

            Instant cutoff = now.minusSeconds(thresholdConfig.days * 86400L);
            List<DsarRequestEntity> candidates = dsarRequestRepository.findEscalationCandidates(cutoff);

            if (candidates.isEmpty()) {
                continue;
            }

            for (DsarRequestEntity dsar : candidates) {
                try {
                    evaluateThreshold(dsar, thresholdConfig, now);
                } catch (Exception ex) {
                    log.error("Failed SLA escalation check for DSAR {} threshold {}", dsar.getRequestIdPk(), thresholdConfig.threshold, ex);
                }
            }
        }
    }

    private void evaluateThreshold(DsarRequestEntity dsar, ThresholdConfig config, Instant now) {
        if (dsar.getCreatedAt() == null) {
            return;
        }

        if ("CLOSED".equals(dsar.getStatus())) {
            return;
        }

        DsarEscalationTaskEntity task = createTaskIfAbsent(dsar, config, now);
        if (task == null) {
            return;
        }

        if (task.getStatus() == EscalationStatus.SUPPRESSED_CLOSED) {
            return;
        }

        if ("CLOSED".equals(dsar.getStatus())) {
            if (task.getStatus() != EscalationStatus.SUPPRESSED_CLOSED) {
                task.setStatus(EscalationStatus.SUPPRESSED_CLOSED);
                escalationTaskRepository.save(task);
            }
            return;
        }

        if (task.getStatus() == EscalationStatus.NOTIFICATION_QUEUED
            || task.getStatus() == EscalationStatus.NOTIFICATION_SENT) {
            return;
        }

        List<String> recipients = resolveRecipients();
        if (recipients.isEmpty()) {
            task.setLastError("No escalation recipients configured");
            escalationTaskRepository.save(task);
            return;
        }

        String requestRef = dsar.getRequestIdPk() + ":" + config.threshold;
        Map<String, String> variables = buildVariables(dsar, config, now);

        try {
            NotificationSendResult result = notificationServiceClient.sendEscalationEmail(
                dsar.getTenantId(),
                requestRef,
                properties.getNotification().getTemplateKey(),
                properties.getNotification().getLanguage(),
                recipients,
                variables
            );

            if (result.notificationRequestId() == null || result.notificationRequestId().isBlank()) {
                task.setLastError("Notification request id missing");
                escalationTaskRepository.save(task);
                return;
            }

            task.setNotificationRequestId(result.notificationRequestId());
            task.setProviderMessageId(result.providerMessageId());
            task.setLastError(null);
            if (result.providerMessageId() != null && !result.providerMessageId().isBlank()) {
                task.setStatus(EscalationStatus.NOTIFICATION_SENT);
            } else {
                task.setStatus(EscalationStatus.NOTIFICATION_QUEUED);
            }
            escalationTaskRepository.save(task);

            writeNotificationQueued(dsar, task, config, now);
            if (task.getStatus() == EscalationStatus.NOTIFICATION_SENT) {
                writeNotificationSent(dsar, task, config, now);
            }
        } catch (RuntimeException ex) {
            task.setLastError(truncate(ex.getMessage(), 500));
            escalationTaskRepository.save(task);
        }
    }

    private DsarEscalationTaskEntity createTaskIfAbsent(DsarRequestEntity dsar, ThresholdConfig config, Instant now) {
        DsarEscalationTaskEntity existing = escalationTaskRepository
            .findByTenantIdAndDsarIdAndThreshold(dsar.getTenantId(), dsar.getRequestIdPk(), config.threshold)
            .orElse(null);
        if (existing != null) {
            return existing;
        }

        DsarEscalationTaskEntity task = new DsarEscalationTaskEntity();
        task.setTenantId(dsar.getTenantId());
        task.setDsarId(dsar.getRequestIdPk());
        task.setThreshold(config.threshold);
        task.setThresholdDays(config.days);
        task.setDueAt(dsar.getCreatedAt().plusSeconds(config.days * 86400L));
        task.setReachedAt(now);
        task.setStatus(EscalationStatus.CREATED);

        try {
            DsarEscalationTaskEntity saved = escalationTaskRepository.save(task);
            writeThresholdReached(dsar, saved, config, now);
            writeTaskCreated(dsar, saved, config, now);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            return escalationTaskRepository
                .findByTenantIdAndDsarIdAndThreshold(dsar.getTenantId(), dsar.getRequestIdPk(), config.threshold)
                .orElse(null);
        }
    }

    private List<String> resolveRecipients() {
        List<String> recipients = new ArrayList<>();
        if (properties.getRecipients() != null && properties.getRecipients().getEmails() != null) {
            recipients.addAll(properties.getRecipients().getEmails());
        }
        return recipients.stream()
            .filter(r -> r != null && !r.isBlank())
            .distinct()
            .toList();
    }

    private Map<String, String> buildVariables(DsarRequestEntity dsar, ThresholdConfig config, Instant now) {
        Map<String, String> variables = new HashMap<>();
        variables.put("dsarId", dsar.getRequestIdPk().toString());
        variables.put("thresholdDays", String.valueOf(config.days));
        variables.put("createdAt", dsar.getCreatedAt() != null ? dsar.getCreatedAt().toString() : "");
        variables.put("dueAt", dsar.getDueAt() != null ? dsar.getDueAt().toString() : "");
        variables.put("status", dsar.getStatus());
        variables.put("severity", config.severity);
        variables.put("detectedAt", now.toString());
        variables.put("fromName", properties.getNotification().getFromName());
        variables.put("category", properties.getNotification().getCategory());
        variables.put("subject", String.format("DSAR SLA %s — DSAR %s", config.severity, dsar.getRequestIdPk()));
        return variables;
    }

    private void writeThresholdReached(DsarRequestEntity dsar, DsarEscalationTaskEntity task, ThresholdConfig config, Instant now) {
        Map<String, Object> payload = Map.of(
            "tenantId", dsar.getTenantId().toString(),
            "dsarId", dsar.getRequestIdPk().toString(),
            "threshold", config.threshold.name(),
            "thresholdDays", config.days,
            "createdAt", dsar.getCreatedAt() != null ? dsar.getCreatedAt().toString() : "",
            "detectedAt", now.toString()
        );
        writeAudit(dsar.getTenantId(), "DSAR_SLA_THRESHOLD_REACHED", "dsar_escalation_task", task.getId().toString(), payload);
        writeOutbox(dsar.getTenantId(), "dsar.sla_threshold_reached", "dsar_escalation_task", task.getId().toString(), payload);
    }

    private void writeTaskCreated(DsarRequestEntity dsar, DsarEscalationTaskEntity task, ThresholdConfig config, Instant now) {
        Map<String, Object> payload = Map.of(
            "tenantId", dsar.getTenantId().toString(),
            "dsarId", dsar.getRequestIdPk().toString(),
            "threshold", config.threshold.name(),
            "thresholdDays", config.days,
            "createdAt", dsar.getCreatedAt() != null ? dsar.getCreatedAt().toString() : "",
            "detectedAt", now.toString()
        );
        writeAudit(dsar.getTenantId(), "DSAR_ESCALATION_TASK_CREATED", "dsar_escalation_task", task.getId().toString(), payload);
        writeOutbox(dsar.getTenantId(), "dsar.escalation_task_created", "dsar_escalation_task", task.getId().toString(), payload);
    }

    private void writeNotificationQueued(DsarRequestEntity dsar, DsarEscalationTaskEntity task, ThresholdConfig config, Instant now) {
        Map<String, Object> payload = Map.of(
            "tenantId", dsar.getTenantId().toString(),
            "dsarId", dsar.getRequestIdPk().toString(),
            "threshold", config.threshold.name(),
            "thresholdDays", config.days,
            "notificationRequestId", task.getNotificationRequestId(),
            "detectedAt", now.toString()
        );
        writeAudit(dsar.getTenantId(), "DSAR_ESCALATION_NOTIFICATION_QUEUED", "dsar_escalation_task", task.getId().toString(), payload);
        writeOutbox(dsar.getTenantId(), "dsar.escalation_notification_queued", "dsar_escalation_task", task.getId().toString(), payload);
    }

    private void writeNotificationSent(DsarRequestEntity dsar, DsarEscalationTaskEntity task, ThresholdConfig config, Instant now) {
        Map<String, Object> payload = Map.of(
            "tenantId", dsar.getTenantId().toString(),
            "dsarId", dsar.getRequestIdPk().toString(),
            "threshold", config.threshold.name(),
            "thresholdDays", config.days,
            "notificationRequestId", task.getNotificationRequestId(),
            "providerMessageId", task.getProviderMessageId() != null ? task.getProviderMessageId() : "",
            "detectedAt", now.toString()
        );
        // "SENT" means the provider accepted the request and assigned a message ID; not guaranteed delivered/opened.
        writeAudit(dsar.getTenantId(), "DSAR_ESCALATION_NOTIFICATION_SENT", "dsar_escalation_task", task.getId().toString(), payload);
        writeOutbox(dsar.getTenantId(), "dsar.escalation_notification_sent", "dsar_escalation_task", task.getId().toString(), payload);
    }

    private void writeAudit(UUID tenantId, String action, String entityType, String entityId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = computeHash(payloadJson);

            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(null)
                .actorType(AuditEvent.ActorType.SYSTEM)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payloadHash(payloadHash)
                .build();

            auditWriter.write(auditEvent);
        } catch (Exception ex) {
            log.error("Failed to write audit event {} for {}", action, entityId, ex);
        }
    }

    private void writeOutbox(UUID tenantId, String eventType, String entityType, String entityId, Map<String, Object> payload) {
        withTenantContext(tenantId, () -> {
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "dsar-grievance-service",
                entityType,
                entityId,
                payload
            );
            outboxWriter.write(event);
        });
    }

    private void withTenantContext(UUID tenantId, Runnable task) {
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

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static class ThresholdConfig {
        private final EscalationThreshold threshold;
        private final Integer days;
        private final String severity;

        private ThresholdConfig(EscalationThreshold threshold, Integer days, String severity) {
            this.threshold = threshold;
            this.days = days;
            this.severity = severity;
        }
    }
}
