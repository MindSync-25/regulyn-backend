package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.notification.dto.GetActiveTemplateResponse;
import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.entity.NotificationDispatchLog;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.integration.EvidenceClient;
import com.regulyn.notification.provider.EmailSendCommand;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import com.regulyn.notification.repository.NotificationDispatchLogRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class NotificationSendService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationSendService.class);
    
    private final TemplateManagementService templateService;
    private final PreferenceService preferenceService;
    private final ConsentCheckService consentCheckService;
    private final NotificationRequestRepository requestRepository;
    private final NotificationDispatchLogRepository dispatchLogRepository;
    private final NotificationMessageRepository messageRepository;
    private final List<NotificationProvider> providers;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final EvidenceClient evidenceClient;
    
    public NotificationSendService(
        TemplateManagementService templateService,
        PreferenceService preferenceService,
        ConsentCheckService consentCheckService,
        NotificationRequestRepository requestRepository,
        NotificationDispatchLogRepository dispatchLogRepository,
        NotificationMessageRepository messageRepository,
        List<NotificationProvider> providers,
        ObjectMapper objectMapper,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        EvidenceClient evidenceClient
    ) {
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.consentCheckService = consentCheckService;
        this.requestRepository = requestRepository;
        this.dispatchLogRepository = dispatchLogRepository;
        this.messageRepository = messageRepository;
        this.providers = providers;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.evidenceClient = evidenceClient;
    }
    
    @Transactional
    public SendNotificationResponse sendNotification(SendNotificationRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Check for duplicate request_ref
        if (request.requestRef() != null && !request.requestRef().isBlank()) {
            Optional<NotificationRequest> existing = requestRepository
                .findByTenantIdAndRequestRef(tenantId, request.requestRef());
            if (existing.isPresent()) {
                NotificationRequest existingRequest = existing.get();
                Map<String, Object> metadata = Map.of(
                    "request_ref", request.requestRef(),
                    "reason", "duplicate_request_ref"
                );
                writeAuditAndOutbox(
                    "NOTIFICATION_IDEMPOTENT_REPLAY",
                    "NotificationRequest",
                    existingRequest.getRequestId().toString(),
                    metadata,
                    computePayloadHash(metadata)
                );
                return new SendNotificationResponse(
                    existingRequest.getRequestId(),
                    existingRequest.getTotalRecipients(),
                    existingRequest.getSentCount(),
                    existingRequest.getSkippedCount(),
                    List.of()
                );
            }
        }
        
        // Get active template
        GetActiveTemplateResponse template = templateService.getActiveTemplate(
            request.templateKey(), 
            request.language()
        );
        
        // Get language variant (fallback to default if requested language not available)
        GetActiveTemplateResponse.LanguageVariant languageVariant = template.languages().stream()
            .filter(l -> l.language().equals(request.language()))
            .findFirst()
            .orElseGet(() -> template.languages().stream()
                .filter(l -> l.language().equals(template.defaultLanguage()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No language variant available"))
            );
        
        // Resolve recipients
        List<String> recipientIds = resolveRecipients(request.audience());
        
        // Create notification request record
        NotificationRequest notificationRequest = new NotificationRequest();
        notificationRequest.setTenantId(tenantId);
        notificationRequest.setRequestRef(request.requestRef());
        notificationRequest.setTemplateId(template.templateId());
        notificationRequest.setVersionId(template.activeVersionId());
        notificationRequest.setLanguage(request.language());
        notificationRequest.setChannel(request.channel());
        notificationRequest.setAudienceType(request.audience().type());
        notificationRequest.setAudienceDataPrincipalId(request.audience().dataPrincipalId());
        notificationRequest.setAudienceUserIds(serializeUserIds(request.audience().userIds()));
        notificationRequest.setVariables(request.variables());
        notificationRequest.setTotalRecipients(recipientIds.size());
        notificationRequest.setCreatedBy(userId);
        
        notificationRequest = requestRepository.save(notificationRequest);
        
        // Dispatch to recipients
        List<SendNotificationResponse.DispatchStatus> dispatches = new ArrayList<>();
        int sentCount = 0;
        int skippedCount = 0;
        
        for (String recipientId : recipientIds) {
            SendNotificationResponse.DispatchStatus status = dispatchToRecipient(
                notificationRequest,
                recipientId,
                template.templateKey(),
                template.category(),
                request.channel(),
                languageVariant,
                request.variables()
            );
            dispatches.add(status);
            
            if ("SENT".equals(status.status())) {
                sentCount++;
            } else if ("SKIPPED_OPT_OUT".equals(status.status()) || "CONSENT_BLOCKED".equals(status.status())) {
                skippedCount++;
            }
        }
        
        // Update counts
        notificationRequest.setSentCount(sentCount);
        notificationRequest.setSkippedCount(skippedCount);
        requestRepository.save(notificationRequest);
        
        Map<String, Object> metadata = Map.of(
            "request_id", notificationRequest.getRequestId().toString(),
            "template_id", notificationRequest.getTemplateId().toString(),
            "template_key", template.templateKey(),
            "template_version_id", notificationRequest.getVersionId().toString(),
            "channel", notificationRequest.getChannel(),
            "language", notificationRequest.getLanguage(),
            "recipient_count", recipientIds.size(),
            "sent_count", sentCount,
            "skipped_count", skippedCount
        );
        writeAuditAndOutbox(
            "NOTIFICATION_SEND_REQUESTED",
            "NotificationRequest",
            notificationRequest.getRequestId().toString(),
            metadata,
            computePayloadHash(metadata)
        );
        
        return new SendNotificationResponse(
            notificationRequest.getRequestId(),
            recipientIds.size(),
            sentCount,
            skippedCount,
            dispatches
        );
    }
    
    private SendNotificationResponse.DispatchStatus dispatchToRecipient(
        NotificationRequest request,
        String recipientId,
        String templateKey,
        String category,
        String channel,
        GetActiveTemplateResponse.LanguageVariant languageVariant,
        Map<String, String> variables
    ) {
        UUID tenantUuid = TenantContextHolder.getTenantId();
        String tenantId = tenantUuid.toString();
        
        // Perform variable substitution
        String finalSubject = substituteVariables(languageVariant.subject(), variables);
        String finalBody = substituteVariables(languageVariant.body(), variables);
        
        // Get recipient address (simplified - in real implementation, lookup from user service)
        String recipientAddress = recipientId + "@example.com";
        
        // Calculate message hash (requestId|recipient|templateId|category)
        String templateIdentity = request.getTemplateId() != null
            ? request.getTemplateId().toString()
            : (templateKey + ":" + (request.getVersionId() != null ? request.getVersionId() : ""));
        String hashInput = request.getRequestId() + "|" + recipientAddress + "|" + templateIdentity + "|" + category;
        byte[] messageHashBytes = sha256Bytes(hashInput);
        String messageHashHex = toHex(messageHashBytes);
        
        // Insert message with QUEUED status
        NotificationMessage message = new NotificationMessage();
        message.setTenantId(tenantUuid);
        message.setNotificationRequestId(request.getRequestId());
        message.setRecipient(recipientAddress);
        message.setChannel(channel);
        message.setCategory(category);
        message.setTemplateKey(templateKey);
        message.setTemplateId(request.getTemplateId());
        message.setTemplateVersionId(request.getVersionId());
        message.setLanguage(request.getLanguage());
        message.setMessageHash(messageHashBytes);
        message.setMessageHashHex(messageHashHex);
        message.setStatus("QUEUED");
        
        try {
            message = messageRepository.save(message);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            Optional<NotificationMessage> existing = messageRepository.findByTenantIdAndMessageHashHex(tenantUuid, messageHashHex);
            if (existing.isPresent()) {
                NotificationMessage existingMessage = existing.get();
                NotificationDispatchLog log = new NotificationDispatchLog();
                log.setTenantId(tenantId);
                log.setRequestId(request.getRequestId());
                log.setRecipientId(recipientId);
                log.setRecipientAddress(recipientAddress);
                log.setStatus("FAILED");
                log.setMessageSubject(finalSubject);
                log.setMessageBody(finalBody);
                log.setMessageHash(messageHashHex);
                log.setSkipReason("Duplicate message hash detected; skipping send");
                dispatchLogRepository.save(log);
                
                writeAuditAndOutbox(
                    "NOTIFICATION_IDEMPOTENT_REPLAY",
                    "NotificationMessage",
                    existingMessage.getId().toString(),
                    Map.of(
                        "notification_request_id", request.getRequestId().toString(),
                        "notification_message_id", existingMessage.getId().toString(),
                        "recipient", maskEmail(recipientAddress),
                        "channel", channel,
                        "category", category,
                        "template_key", templateKey,
                        "message_hash_hex", messageHashHex
                    ),
                    messageHashHex
                );
                
                return new SendNotificationResponse.DispatchStatus(
                    recipientId,
                    "IDEMPOTENT_REPLAY",
                    "Duplicate message hash; send skipped"
                );
            }
            throw ex;
        }
        
        writeAuditAndOutbox(
            "NOTIFICATION_QUEUED",
            "NotificationMessage",
            message.getId().toString(),
            Map.of(
                "notification_request_id", request.getRequestId().toString(),
                "notification_message_id", message.getId().toString(),
                "recipient", maskEmail(recipientAddress),
                "channel", channel,
                "category", category,
                "template_key", templateKey,
                "message_hash_hex", messageHashHex
            ),
            messageHashHex
        );

        ConsentDecision consentDecision = consentCheckService.checkConsent(
            tenantId,
            recipientId,
            channel,
            category
        );

        if (consentDecision.outcome() == ConsentDecision.Outcome.BLOCK
            || consentDecision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK) {
            return handleConsentBlocked(request, message, recipientId, recipientAddress, category, channel, consentDecision);
        }

        if (consentDecision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_ALLOW) {
            emitConsentCheckFailedEvents(request, message, recipientAddress, category, channel, consentDecision);
        }
        
        // Find provider for channel
        NotificationProvider provider = providers.stream()
            .filter(p -> p.getChannel().equals(channel))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No provider for channel: " + channel));
        
        EmailSendCommand command = new EmailSendCommand(
            tenantUuid,
            request.getRequestId(),
            message.getId(),
            recipientAddress,
            finalSubject,
            finalBody,
            languageVariant.format(),
            messageHashHex
        );
        ProviderResult result = provider.sendEmail(command);
        
        NotificationDispatchLog log = new NotificationDispatchLog();
        log.setTenantId(tenantId);
        log.setRequestId(request.getRequestId());
        log.setRecipientId(recipientId);
        log.setRecipientAddress(recipientAddress);
        log.setMessageSubject(finalSubject);
        log.setMessageBody(finalBody);
        log.setMessageHash(messageHashHex);
        boolean isOptedOut = preferenceService.isOptedOut(recipientId, channel, category);
        if (isOptedOut && !"MARKETING".equals(category)) {
            log.setSkipReason("User opted out but " + category + " notification allowed");
        }
        
        if (result.isSuccess()) {
            message.setStatus("SENT");
            message.setAttemptCount(message.getAttemptCount() + 1);
            message.setLastAttemptAt(Instant.now());
            message.setLastFailureReason(null);
            message.setNextAttemptAt(null);
            
            String idempotencyKey = tenantId + ":" + message.getId() + ":NOTIFICATION_SENT";
            String evidenceRef = evidenceClient.createNotificationSentArtifact(
                buildEvidencePayload(request, message, recipientAddress, messageHashHex),
                idempotencyKey
            );
            message.setSentEvidenceArtifactRef(evidenceRef);
            messageRepository.save(message);
            
            log.setStatus("SENT");
            dispatchLogRepository.save(log);
            
            writeAuditAndOutbox(
                "NOTIFICATION_SENT",
                "NotificationMessage",
                message.getId().toString(),
                Map.ofEntries(
                    Map.entry("notification_request_id", request.getRequestId().toString()),
                    Map.entry("notification_message_id", message.getId().toString()),
                    Map.entry("recipient", maskEmail(recipientAddress)),
                    Map.entry("channel", channel),
                    Map.entry("category", category),
                    Map.entry("template_key", templateKey),
                    Map.entry("template_id", request.getTemplateId().toString()),
                    Map.entry("template_version_id", request.getVersionId().toString()),
                    Map.entry("message_hash_hex", messageHashHex),
                    Map.entry("evidence_artifact_ref", evidenceRef),
                    Map.entry("provider", result.getProviderName()),
                    Map.entry("provider_message_id", result.getProviderMessageId())
                ),
                messageHashHex
            );
            
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "SENT",
                null
            );
        }
        
        int attempts = message.getAttemptCount() + 1;
        message.setAttemptCount(attempts);
        message.setLastAttemptAt(Instant.now());
        message.setLastFailureReason(truncate(result.getFailureReason(), 500));
        boolean retryable = result.isRetryable() && attempts < message.getMaxAttempts();
        message.setStatus(retryable ? "FAILED_RETRYABLE" : "FAILED_TERMINAL");
        message.setNextAttemptAt(retryable ? computeNextAttemptAt(attempts) : null);
        messageRepository.save(message);
        
        log.setStatus("FAILED");
        dispatchLogRepository.save(log);
        
        String action = retryable ? "NOTIFICATION_FAILED_RETRYABLE" : "NOTIFICATION_FAILED_TERMINAL";
        writeAuditAndOutbox(
            action,
            "NotificationMessage",
            message.getId().toString(),
            Map.ofEntries(
                Map.entry("notification_request_id", request.getRequestId().toString()),
                Map.entry("notification_message_id", message.getId().toString()),
                Map.entry("recipient", maskEmail(recipientAddress)),
                Map.entry("channel", channel),
                Map.entry("category", category),
                Map.entry("template_key", templateKey),
                Map.entry("template_id", request.getTemplateId().toString()),
                Map.entry("template_version_id", request.getVersionId().toString()),
                Map.entry("message_hash_hex", messageHashHex),
                Map.entry("failure_reason", truncate(result.getFailureReason(), 200)),
                Map.entry("provider", result.getProviderName())
            ),
            messageHashHex
        );
        
        return new SendNotificationResponse.DispatchStatus(
            recipientId,
            message.getStatus(),
            result.getFailureReason()
        );
    }

    private SendNotificationResponse.DispatchStatus handleConsentBlocked(
        NotificationRequest request,
        NotificationMessage message,
        String recipientId,
        String recipientAddress,
        String category,
        String channel,
        ConsentDecision decision
    ) {
        String reasonCode = decision.reasonCode() != null ? decision.reasonCode() : "CONSENT_BLOCKED";
        message.setStatus("CONSENT_BLOCKED");
        message.setLastFailureReason(reasonCode);
        message.setNextAttemptAt(null);

        if (message.getFailedEvidenceArtifactRef() == null) {
            String eventType = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
                ? "NOTIFICATION_CONSENT_CHECK_FAILED"
                : "NOTIFICATION_CONSENT_BLOCKED";
            String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":" + eventType;
            String evidenceRef = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
                ? evidenceClient.createNotificationConsentCheckFailedArtifact(
                    buildConsentEvidencePayload(request, message, recipientAddress, reasonCode, eventType),
                    idempotencyKey
                )
                : evidenceClient.createNotificationConsentBlockedArtifact(
                    buildConsentEvidencePayload(request, message, recipientAddress, reasonCode, eventType),
                    idempotencyKey
                );
            message.setFailedEvidenceArtifactRef(evidenceRef);
        }
        messageRepository.save(message);

        NotificationDispatchLog log = new NotificationDispatchLog();
        log.setTenantId(message.getTenantId().toString());
        log.setRequestId(request.getRequestId());
        log.setRecipientId(recipientId);
        log.setRecipientAddress(recipientAddress);
        log.setStatus("SKIPPED_OPT_OUT");
        log.setSkipReason(reasonCode);
        log.setMessageHash(message.getMessageHashHex());
        dispatchLogRepository.save(log);

        String action = decision.outcome() == ConsentDecision.Outcome.CHECK_FAILED_BLOCK
            ? "NOTIFICATION_CONSENT_CHECK_FAILED"
            : "NOTIFICATION_CONSENT_BLOCKED";
        writeAuditAndOutbox(
            action,
            "NotificationMessage",
            message.getId().toString(),
            buildConsentMetadata(request, message, recipientAddress, category, channel, reasonCode),
            message.getMessageHashHex()
        );

        return new SendNotificationResponse.DispatchStatus(
            recipientId,
            "CONSENT_BLOCKED",
            reasonCode
        );
    }

    private void emitConsentCheckFailedEvents(
        NotificationRequest request,
        NotificationMessage message,
        String recipientAddress,
        String category,
        String channel,
        ConsentDecision decision
    ) {
        String reasonCode = decision.reasonCode() != null ? decision.reasonCode() : "CONSENT_CHECK_FAILED";
        Map<String, Object> metadata = buildConsentMetadata(request, message, recipientAddress, category, channel, reasonCode);

        writeAuditAndOutbox(
            "NOTIFICATION_CONSENT_CHECK_FAILED",
            "NotificationMessage",
            message.getId().toString(),
            metadata,
            message.getMessageHashHex()
        );

        String idempotencyKey = message.getTenantId() + ":" + message.getId() + ":NOTIFICATION_CONSENT_CHECK_FAILED";
        evidenceClient.createNotificationConsentCheckFailedArtifact(
            buildConsentEvidencePayload(request, message, recipientAddress, reasonCode, "NOTIFICATION_CONSENT_CHECK_FAILED"),
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
                buildConsentEvidencePayload(request, message, recipientAddress, reasonCode, "NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL"),
                bypassKey
            );
        }
    }

    private Map<String, Object> buildConsentMetadata(
        NotificationRequest request,
        NotificationMessage message,
        String recipientAddress,
        String category,
        String channel,
        String reasonCode
    ) {
        return Map.ofEntries(
            Map.entry("notification_request_id", request.getRequestId().toString()),
            Map.entry("notification_message_id", message.getId().toString()),
            Map.entry("recipient", maskEmail(recipientAddress)),
            Map.entry("channel", channel),
            Map.entry("category", category),
            Map.entry("template_key", message.getTemplateKey()),
            Map.entry("template_id", request.getTemplateId() != null ? request.getTemplateId().toString() : null),
            Map.entry("template_version_id", request.getVersionId() != null ? request.getVersionId().toString() : null),
            Map.entry("message_hash_hex", message.getMessageHashHex()),
            Map.entry("reason_code", reasonCode)
        );
    }

    private Map<String, Object> buildConsentEvidencePayload(
        NotificationRequest request,
        NotificationMessage message,
        String recipientAddress,
        String reasonCode,
        String eventType
    ) {
        String userId = TenantContextHolder.getUserId() != null ? TenantContextHolder.getUserId().toString() : "system";
        return Map.of(
            "userId", userId,
            "eventType", eventType,
            "evidenceType", "NOTIFICATION",
            "description", "Notification consent decision",
            "metadata", Map.of(
                "tenant_id", request.getTenantId(),
                "notification_request_id", request.getRequestId().toString(),
                "notification_message_id", message.getId().toString(),
                "category", message.getCategory(),
                "channel", message.getChannel(),
                "recipient", maskEmail(recipientAddress),
                "message_hash_hex", message.getMessageHashHex(),
                "reason_code", reasonCode,
                "timestamp", Instant.now().toString()
            )
        );
    }
    
    private List<String> resolveRecipients(SendNotificationRequest.Audience audience) {
        return switch (audience.type()) {
            case "BOARD" -> {
                // In real implementation, fetch from user service
                yield List.of("board-member-1", "board-member-2");
            }
            case "ALL_USERS" -> {
                // In real implementation, fetch all users
                yield List.of("user-1", "user-2", "user-3");
            }
            case "DATA_PRINCIPAL" -> {
                if (audience.dataPrincipalId() == null || audience.dataPrincipalId().isBlank()) {
                    throw new IllegalArgumentException("dataPrincipalId required for DATA_PRINCIPAL audience");
                }
                yield List.of(audience.dataPrincipalId());
            }
            case "USER_IDS" -> {
                if (audience.userIds() == null || audience.userIds().isEmpty()) {
                    throw new IllegalArgumentException("userIds required for USER_IDS audience");
                }
                yield new ArrayList<>(audience.userIds());
            }
            default -> throw new IllegalArgumentException("Unknown audience type: " + audience.type());
        };
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
    
    private Instant computeNextAttemptAt(int attemptCount) {
        int[] backoffMinutes = new int[]{1, 5, 15, 30, 60};
        int index = Math.min(Math.max(attemptCount - 1, 0), backoffMinutes.length - 1);
        return Instant.now().plus(backoffMinutes[index], ChronoUnit.MINUTES);
    }
    
    private Map<String, Object> buildEvidencePayload(
        NotificationRequest request,
        NotificationMessage message,
        String recipientAddress,
        String messageHashHex
    ) {
        String userId = TenantContextHolder.getUserId() != null ? TenantContextHolder.getUserId().toString() : "system";
        return Map.of(
            "userId", userId,
            "eventType", "NOTIFICATION_SENT",
            "evidenceType", "NOTIFICATION",
            "description", "Notification sent",
            "metadata", Map.of(
                "tenant_id", request.getTenantId(),
                "notification_request_id", request.getRequestId().toString(),
                "notification_message_id", message.getId().toString(),
                "template_key", message.getTemplateKey(),
                "template_id", request.getTemplateId().toString(),
                "template_version_id", request.getVersionId().toString(),
                "language", request.getLanguage(),
                "recipient", maskEmail(recipientAddress),
                "message_hash_hex", messageHashHex,
                "timestamp", Instant.now().toString()
            )
        );
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
    
    private String serializeUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(userIds);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize userIds", e);
        }
    }
}
