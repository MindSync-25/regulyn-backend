package com.regulyn.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.integration.EvidenceClient;
import com.regulyn.notification.provider.EmailSendCommand;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationRetryWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetryWorkerService.class);

    private final NotificationMessageRepository messageRepository;
    private final NotificationRequestRepository requestRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final NotificationTemplateLanguageRepository languageRepository;
    private final List<NotificationProvider> providers;
    private final EvidenceClient evidenceClient;
    private final ConsentCheckService consentCheckService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public NotificationRetryWorkerService(
        NotificationMessageRepository messageRepository,
        NotificationRequestRepository requestRepository,
        NotificationTemplateRepository templateRepository,
        NotificationTemplateVersionRepository versionRepository,
        NotificationTemplateLanguageRepository languageRepository,
        List<NotificationProvider> providers,
        EvidenceClient evidenceClient,
        ConsentCheckService consentCheckService,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        @Value("${notification.retry.batchSize:50}") int batchSize
    ) {
        this.messageRepository = messageRepository;
        this.requestRepository = requestRepository;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.languageRepository = languageRepository;
        this.providers = providers;
        this.evidenceClient = evidenceClient;
        this.consentCheckService = consentCheckService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
    }

    public void runRetryBatch() {
        List<NotificationMessage> claimed = claimDueMessages(batchSize);
        for (NotificationMessage message : claimed) {
            processClaimedMessage(message.getId());
        }
    }

    public List<NotificationMessage> claimDueMessages(int size) {
        return transactionTemplate.execute(status -> {
            List<NotificationMessage> messages = messageRepository.findDueMessagesForRetry(size);
            if (messages.isEmpty()) {
                return List.of();
            }
            for (NotificationMessage message : messages) {
                message.setStatus("RETRY_IN_PROGRESS");
            }
            messageRepository.saveAll(messages);

            for (NotificationMessage message : messages) {
                withTenantContext(message.getTenantId(), "retry-claim-" + message.getId(), () -> {
                    Map<String, Object> metadata = buildRetryMetadata(message, null, true);
                    writeAuditAndOutbox(
                        "NOTIFICATION_RETRY_STARTED",
                        "NotificationMessage",
                        message.getId().toString(),
                        metadata,
                        message.getMessageHashHex()
                    );
                });
            }
            return messages;
        });
    }

    private void processClaimedMessage(UUID messageId) {
        Optional<NotificationMessage> messageOpt = messageRepository.findById(messageId);
        if (messageOpt.isEmpty()) {
            return;
        }
        NotificationMessage message = messageOpt.get();
        if ("DELIVERED".equals(message.getStatus()) || "FAILED_TERMINAL".equals(message.getStatus())) {
            return;
        }
        if (!"RETRY_IN_PROGRESS".equals(message.getStatus())) {
            return;
        }

        withTenantContext(message.getTenantId(), "retry-" + message.getId(), () -> {
            try {
                NotificationRequest request = requestRepository.findById(message.getNotificationRequestId())
                    .orElseThrow(() -> new IllegalStateException("Notification request not found"));

                String recipientId = resolveRecipientIdentifier(request, message.getRecipient());
                ConsentDecision consentDecision = consentCheckService.checkConsent(
                    message.getTenantId().toString(),
                    recipientId,
                    message.getChannel(),
                    message.getCategory()
                );

                if (consentDecision.outcome() == ConsentDecision.Outcome.BLOCK
                    || consentDecision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK) {
                    handleConsentBlocked(message, request, consentDecision, recipientId);
                    return;
                }

                if (consentDecision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_ALLOW) {
                    emitConsentCheckFailedEvents(message, request, consentDecision, recipientId);
                }

                int attempts = message.getAttemptCount() + 1;
                message.setAttemptCount(attempts);
                message.setLastAttemptAt(Instant.now());
                messageRepository.save(message);

                NotificationTemplate template = templateRepository.findByTenantIdAndTemplateId(
                        message.getTenantId().toString(),
                        message.getTemplateId()
                    )
                    .orElseThrow(() -> new IllegalStateException("Template not found"));

                NotificationTemplateVersion version = versionRepository.findByTenantIdAndVersionId(
                        message.getTenantId().toString(),
                        message.getTemplateVersionId()
                    )
                    .orElseThrow(() -> new IllegalStateException("Template version not found"));

                NotificationTemplateLanguage language = resolveLanguageVariant(
                    message.getTenantId().toString(),
                    version.getVersionId(),
                    message.getLanguage(),
                    template.getDefaultLanguage()
                );

                String subject = substituteVariables(language.getSubject(), request.getVariables());
                String body = substituteVariables(language.getBody(), request.getVariables());

                NotificationProvider provider = providers.stream()
                    .filter(p -> p.getChannel().equals(message.getChannel()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No provider for channel: " + message.getChannel()));

                EmailSendCommand command = new EmailSendCommand(
                    message.getTenantId(),
                    request.getRequestId(),
                    message.getId(),
                    message.getRecipient(),
                    subject,
                    body,
                    language.getFormat(),
                    message.getMessageHashHex()
                );

                ProviderResult result = provider.sendEmail(command);
                if (result.isSuccess()) {
                    handleSuccess(message, request, result);
                    return;
                }

                handleFailure(message, result, attempts, request);
            } catch (Exception ex) {
                handleUnexpectedFailure(message, ex);
            }
        });
    }

    private void handleConsentBlocked(
        NotificationMessage message,
        NotificationRequest request,
        ConsentDecision decision,
        String recipientId
    ) {
        String reasonCode = decision.reasonCode() != null ? decision.reasonCode() : "CONSENT_BLOCKED";
        message.setStatus("CONSENT_BLOCKED");
        message.setNextAttemptAt(null);
        message.setLastFailureReason(reasonCode);

        if (message.getFailedEvidenceArtifactRef() == null) {
            String eventType = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
                ? "NOTIFICATION_CONSENT_CHECK_FAILED"
                : "NOTIFICATION_CONSENT_BLOCKED";
            String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":" + eventType;
            String evidenceRef = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
                ? evidenceClient.createNotificationConsentCheckFailedArtifact(
                    buildConsentEvidencePayload(request, message, recipientId, reasonCode, eventType),
                    idempotencyKey
                )
                : evidenceClient.createNotificationConsentBlockedArtifact(
                    buildConsentEvidencePayload(request, message, recipientId, reasonCode, eventType),
                    idempotencyKey
                );
            message.setFailedEvidenceArtifactRef(evidenceRef);
        }
        messageRepository.save(message);

        String action = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
            ? "NOTIFICATION_CONSENT_CHECK_FAILED"
            : "NOTIFICATION_CONSENT_BLOCKED";
        writeAuditAndOutbox(
            action,
            "NotificationMessage",
            message.getId().toString(),
            buildConsentMetadata(message, request, recipientId, reasonCode),
            message.getMessageHashHex()
        );
    }

    private void emitConsentCheckFailedEvents(
        NotificationMessage message,
        NotificationRequest request,
        ConsentDecision decision,
        String recipientId
    ) {
        String reasonCode = decision.reasonCode() != null ? decision.reasonCode() : "CONSENT_CHECK_FAILED";
        Map<String, Object> metadata = buildConsentMetadata(message, request, recipientId, reasonCode);
        writeAuditAndOutbox(
            "NOTIFICATION_CONSENT_CHECK_FAILED",
            "NotificationMessage",
            message.getId().toString(),
            metadata,
            message.getMessageHashHex()
        );

        String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":NOTIFICATION_CONSENT_CHECK_FAILED";
        evidenceClient.createNotificationConsentCheckFailedArtifact(
            buildConsentEvidencePayload(request, message, recipientId, reasonCode, "NOTIFICATION_CONSENT_CHECK_FAILED"),
            idempotencyKey
        );

        if (decision.bypassedDueToLegal()) {
            writeAuditAndOutbox(
                "NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL",
                "NotificationMessage",
                message.getId().toString(),
                metadata,
                message.getMessageHashHex()
            );
            String bypassKey = message.getTenantId() + ":" + message.getId() + ":NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL";
            evidenceClient.createNotificationBypassedConsentArtifact(
                buildConsentEvidencePayload(request, message, recipientId, reasonCode, "NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL"),
                bypassKey
            );
        }
    }

    private NotificationTemplateLanguage resolveLanguageVariant(
        String tenantId,
        UUID versionId,
        String requestedLanguage,
        String defaultLanguage
    ) {
        Optional<NotificationTemplateLanguage> exact = languageRepository
            .findByTenantIdAndVersionIdAndLanguage(tenantId, versionId, requestedLanguage);
        if (exact.isPresent()) {
            return exact.get();
        }
        if (defaultLanguage != null) {
            Optional<NotificationTemplateLanguage> fallback = languageRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, versionId, defaultLanguage);
            if (fallback.isPresent()) {
                return fallback.get();
            }
        }
        List<NotificationTemplateLanguage> all = languageRepository.findByTenantIdAndVersionId(tenantId, versionId);
        if (all.isEmpty()) {
            throw new IllegalStateException("No template language variants available");
        }
        return all.get(0);
    }

    private void handleSuccess(NotificationMessage message, NotificationRequest request, ProviderResult result) {
        message.setStatus("SENT");
        message.setNextAttemptAt(null);
        message.setLastFailureReason(null);

        if (message.getSentEvidenceArtifactRef() == null) {
            String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":NOTIFICATION_SENT";
            String evidenceRef = evidenceClient.createNotificationSentArtifact(
                buildSentEvidencePayload(request, message),
                idempotencyKey
            );
            message.setSentEvidenceArtifactRef(evidenceRef);
        }
        messageRepository.save(message);

        Map<String, Object> metadata = buildRetryMetadata(message, request, true);
        metadata.put("provider", result.getProviderName());
        if (result.getProviderMessageId() != null) {
            metadata.put("provider_message_id", result.getProviderMessageId());
        }
        if (message.getSentEvidenceArtifactRef() != null) {
            metadata.put("evidence_artifact_ref", message.getSentEvidenceArtifactRef());
        }
        writeAuditAndOutbox(
            "NOTIFICATION_SENT",
            "NotificationMessage",
            message.getId().toString(),
            metadata,
            message.getMessageHashHex()
        );
    }

    private void handleFailure(NotificationMessage message, ProviderResult result, int attempts, NotificationRequest request) {
        boolean retryable = result.isRetryable() && attempts < message.getMaxAttempts();
        String reason = truncate(result.getFailureReason(), 500);
        message.setLastFailureReason(reason);

        if (retryable) {
            message.setStatus("FAILED_RETRYABLE");
            message.setNextAttemptAt(computeNextAttemptAt(attempts));
            messageRepository.save(message);

            Map<String, Object> metadata = buildRetryMetadata(message, request, true);
            metadata.put("failure_reason", truncate(result.getFailureReason(), 200));
            metadata.put("next_attempt_at", message.getNextAttemptAt() != null ? message.getNextAttemptAt().toString() : null);
            metadata.put("provider", result.getProviderName());
            writeAuditAndOutbox(
                "NOTIFICATION_FAILED_RETRYABLE",
                "NotificationMessage",
                message.getId().toString(),
                metadata,
                message.getMessageHashHex()
            );
            return;
        }

        message.setStatus("FAILED_TERMINAL");
        message.setNextAttemptAt(null);
        if (message.getFailedEvidenceArtifactRef() == null) {
            String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":NOTIFICATION_FAILED";
            String evidenceRef = evidenceClient.createNotificationFailedArtifact(
                buildFailedEvidencePayload(message, result),
                idempotencyKey
            );
            message.setFailedEvidenceArtifactRef(evidenceRef);
        }
        messageRepository.save(message);

        Map<String, Object> metadata = buildRetryMetadata(message, request, true);
        metadata.put("failure_reason", truncate(result.getFailureReason(), 200));
        metadata.put("provider", result.getProviderName());
        if (message.getFailedEvidenceArtifactRef() != null) {
            metadata.put("evidence_artifact_ref", message.getFailedEvidenceArtifactRef());
        }
        writeAuditAndOutbox(
            "NOTIFICATION_FAILED_TERMINAL",
            "NotificationMessage",
            message.getId().toString(),
            metadata,
            message.getMessageHashHex()
        );
    }

    private void handleUnexpectedFailure(NotificationMessage message, Exception ex) {
        int attempts = message.getAttemptCount() != null ? message.getAttemptCount() : 1;
        message.setStatus("FAILED_RETRYABLE");
        message.setNextAttemptAt(Instant.now().plus(60, ChronoUnit.MINUTES));
        message.setLastFailureReason(truncate(ex.getMessage(), 500));
        messageRepository.save(message);

        Map<String, Object> metadata = buildRetryMetadata(message, null, true);
        metadata.put("failure_reason", truncate(ex.getMessage(), 200));
        metadata.put("next_attempt_at", message.getNextAttemptAt() != null ? message.getNextAttemptAt().toString() : null);
        metadata.put("attempt_count", attempts);
        writeAuditAndOutbox(
            "NOTIFICATION_FAILED_RETRYABLE",
            "NotificationMessage",
            message.getId().toString(),
            metadata,
            message.getMessageHashHex()
        );
    }

    private Map<String, Object> buildRetryMetadata(NotificationMessage message, NotificationRequest request, boolean retry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenant_id", message.getTenantId().toString());
        metadata.put("notification_message_id", message.getId().toString());
        metadata.put("notification_request_id", message.getNotificationRequestId().toString());
        metadata.put("recipient", maskEmail(message.getRecipient()));
        metadata.put("channel", message.getChannel());
        metadata.put("category", message.getCategory());
        metadata.put("template_key", message.getTemplateKey());
        metadata.put("template_id", message.getTemplateId() != null ? message.getTemplateId().toString() : null);
        metadata.put("template_version_id", message.getTemplateVersionId() != null ? message.getTemplateVersionId().toString() : null);
        metadata.put("message_hash_hex", message.getMessageHashHex());
        metadata.put("attempt_count", message.getAttemptCount());
        metadata.put("max_attempts", message.getMaxAttempts());
        metadata.put("retry", retry);
        if (message.getNextAttemptAt() != null) {
            metadata.put("next_attempt_at", message.getNextAttemptAt().toString());
        }
        if (request != null) {
            metadata.put("language", request.getLanguage());
        }
        return metadata;
    }

    private Map<String, Object> buildConsentMetadata(
        NotificationMessage message,
        NotificationRequest request,
        String recipientId,
        String reasonCode
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenant_id", message.getTenantId().toString());
        metadata.put("notification_message_id", message.getId().toString());
        metadata.put("notification_request_id", message.getNotificationRequestId().toString());
        metadata.put("recipient", maskEmail(recipientId));
        metadata.put("channel", message.getChannel());
        metadata.put("category", message.getCategory());
        metadata.put("template_key", message.getTemplateKey());
        metadata.put("template_id", message.getTemplateId() != null ? message.getTemplateId().toString() : null);
        metadata.put("template_version_id", message.getTemplateVersionId() != null ? message.getTemplateVersionId().toString() : null);
        metadata.put("message_hash_hex", message.getMessageHashHex());
        metadata.put("reason_code", reasonCode);
        if (request != null) {
            metadata.put("language", request.getLanguage());
        }
        return metadata;
    }

    private Map<String, Object> buildConsentEvidencePayload(
        NotificationRequest request,
        NotificationMessage message,
        String recipientId,
        String reasonCode,
        String eventType
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", TenantContextHolder.getUserId() != null ? TenantContextHolder.getUserId().toString() : "system");
        payload.put("eventType", eventType);
        payload.put("evidenceType", "NOTIFICATION");
        payload.put("description", "Notification consent decision");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenant_id", message.getTenantId().toString());
        metadata.put("notification_request_id", message.getNotificationRequestId().toString());
        metadata.put("notification_message_id", message.getId().toString());
        metadata.put("category", message.getCategory());
        metadata.put("channel", message.getChannel());
        metadata.put("recipient", maskEmail(recipientId));
        metadata.put("message_hash_hex", message.getMessageHashHex());
        metadata.put("reason_code", reasonCode);
        metadata.put("timestamp", Instant.now().toString());
        payload.put("metadata", metadata);
        return payload;
    }

    private String resolveRecipientIdentifier(NotificationRequest request, String recipientAddress) {
        if (request.getAudienceDataPrincipalId() != null && !request.getAudienceDataPrincipalId().isBlank()) {
            return request.getAudienceDataPrincipalId();
        }
        String userIdsJson = request.getAudienceUserIds();
        if (userIdsJson != null && !userIdsJson.isBlank()) {
            try {
                List<String> userIds = objectMapper.readValue(userIdsJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                if (!userIds.isEmpty()) {
                    return userIds.get(0);
                }
            } catch (Exception ignored) {
                // fallback to recipient address
            }
        }
        if (recipientAddress == null) {
            return "unknown";
        }
        int atIndex = recipientAddress.indexOf('@');
        return atIndex > 0 ? recipientAddress.substring(0, atIndex) : recipientAddress;
    }

    private Map<String, Object> buildSentEvidencePayload(NotificationRequest request, NotificationMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", TenantContextHolder.getUserId() != null ? TenantContextHolder.getUserId().toString() : "system");
        payload.put("eventType", "NOTIFICATION_SENT");
        payload.put("evidenceType", "NOTIFICATION");
        payload.put("description", "Notification sent");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenant_id", message.getTenantId().toString());
        metadata.put("notification_request_id", message.getNotificationRequestId().toString());
        metadata.put("notification_message_id", message.getId().toString());
        metadata.put("template_key", message.getTemplateKey());
        metadata.put("template_id", request.getTemplateId().toString());
        metadata.put("template_version_id", request.getVersionId().toString());
        metadata.put("language", request.getLanguage());
        metadata.put("recipient", maskEmail(message.getRecipient()));
        metadata.put("message_hash_hex", message.getMessageHashHex());
        metadata.put("timestamp", Instant.now().toString());
        payload.put("metadata", metadata);
        return payload;
    }

    private Map<String, Object> buildFailedEvidencePayload(NotificationMessage message, ProviderResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", message.getTenantId().toString());
        payload.put("notification_message_id", message.getId().toString());
        payload.put("notification_request_id", message.getNotificationRequestId().toString());
        payload.put("message_hash_hex", message.getMessageHashHex());
        payload.put("recipient", maskEmail(message.getRecipient()));
        payload.put("provider", result.getProviderName());
        payload.put("failure_reason", truncate(result.getFailureReason(), 200));
        payload.put("timestamp", Instant.now().toString());
        payload.put("outcome_status", "FAILED_TERMINAL");
        return payload;
    }

    private void writeAuditAndOutbox(
        String action,
        String entityType,
        String entityId,
        Map<String, Object> metadata,
        String payloadHash
    ) {
        auditWriter.auditAction(
            action,
            entityType,
            entityId,
            payloadHash,
            null,
            objectMapper.valueToTree(metadata)
        );

        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(TenantContextHolder.getTenantId());
        event.setActorId(TenantContextHolder.getUserId());
        event.setActorType(TenantContextHolder.getUserId() != null ? com.regulyn.events.model.ActorType.USER : com.regulyn.events.model.ActorType.SYSTEM);
        event.setEventType(action);
        event.setSourceService("notification-service");
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        event.setIdempotencyKey(payloadHash);
        event.setPayload(objectMapper.valueToTree(metadata));
        event.setPayloadHash(payloadHash != null ? payloadHash : computePayloadHash(metadata));
        outboxWriter.write(event);
    }

    private String computePayloadHash(Map<String, Object> metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            return toHex(sha256Bytes(json));
        } catch (JsonProcessingException e) {
            return toHex(sha256Bytes(UUID.randomUUID().toString()));
        }
    }

    private Instant computeNextAttemptAt(int attemptCount) {
        int[] backoffMinutes = new int[]{1, 5, 15, 30, 60};
        int index = Math.min(Math.max(attemptCount - 1, 0), backoffMinutes.length - 1);
        return Instant.now().plus(backoffMinutes[index], ChronoUnit.MINUTES);
    }

    private String substituteVariables(String template, Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String prefix = local.isEmpty() ? "*" : local.substring(0, 1);
        return prefix + "***@" + domain;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void withTenantContext(UUID tenantId, String requestId, Runnable runnable) {
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setRequestId(requestId);
        TenantContextHolder.setContext(context);
        try {
            runnable.run();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
