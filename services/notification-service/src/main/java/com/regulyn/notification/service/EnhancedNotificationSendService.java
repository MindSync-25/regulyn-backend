package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.client.ConsentServiceClient;
import com.regulyn.notification.util.NotificationAuditHelper;
import com.regulyn.notification.client.EvidenceServiceClient;
import com.regulyn.notification.dto.GetActiveTemplateResponse;
import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.entity.DeliveryReceipt;
import com.regulyn.notification.entity.NotificationDispatchLog;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderSendResult;
import com.regulyn.notification.repository.DeliveryReceiptRepository;
import com.regulyn.notification.repository.NotificationDispatchLogRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class EnhancedNotificationSendService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedNotificationSendService.class);
    
    private final TemplateManagementService templateService;
    private final PreferenceService preferenceService;
    private final NotificationRequestRepository requestRepository;
    private final NotificationDispatchLogRepository dispatchLogRepository;
    private final NotificationMessageRepository messageRepository;
    private final DeliveryReceiptRepository receiptRepository;
    private final List<NotificationProvider> providers;
    private final ObjectMapper objectMapper;
    private final NotificationAuditHelper auditHelper;
    
    @Autowired(required = false)
    private ConsentServiceClient consentClient;
    
    @Autowired(required = false)
    private EvidenceServiceClient evidenceClient;
    
    public EnhancedNotificationSendService(
        TemplateManagementService templateService,
        PreferenceService preferenceService,
        NotificationRequestRepository requestRepository,
        NotificationDispatchLogRepository dispatchLogRepository,
        NotificationMessageRepository messageRepository,
        DeliveryReceiptRepository receiptRepository,
        List<NotificationProvider> providers,
        ObjectMapper objectMapper,
        NotificationAuditHelper auditHelper
    ) {
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.requestRepository = requestRepository;
        this.dispatchLogRepository = dispatchLogRepository;
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.providers = providers;
        this.objectMapper = objectMapper;
        this.auditHelper = auditHelper;
    }
    
    @Transactional
    public SendNotificationResponse sendNotification(SendNotificationRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Idempotency: Check for duplicate request_ref
        if (request.requestRef() != null && !request.requestRef().isBlank()) {
            Optional<NotificationRequest> existing = requestRepository
                .findByTenantIdAndRequestRef(tenantId, request.requestRef());
            if (existing.isPresent()) {
                logger.info("Duplicate request_ref detected: {}", request.requestRef());
                throw new IllegalArgumentException("Duplicate request_ref: " + request.requestRef());
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
        
        // Dispatch to recipients with consent, retry, and evidence
        List<SendNotificationResponse.DispatchStatus> dispatches = new ArrayList<>();
        int sentCount = 0;
        int skippedCount = 0;
        int consentBlockedCount = 0;
        
        for (String recipientId : recipientIds) {
            SendNotificationResponse.DispatchStatus status = dispatchToRecipientEnhanced(
                notificationRequest,
                recipientId,
                template.category(),
                request.channel(),
                languageVariant,
                request.variables()
            );
            dispatches.add(status);
            
            if ("SENT".equals(status.status()) || "QUEUED".equals(status.status())) {
                sentCount++;
            } else if ("SKIPPED_OPT_OUT".equals(status.status())) {
                skippedCount++;
            } else if ("CONSENT_BLOCKED".equals(status.status())) {
                consentBlockedCount++;
            }
        }
        
        // Update counts
        notificationRequest.setSentCount(sentCount);
        notificationRequest.setSkippedCount(skippedCount + consentBlockedCount);
        requestRepository.save(notificationRequest);
        
        // Audit
        auditHelper.writeAudit(
            "NOTIFICATION_SEND_REQUESTED",
            "NotificationRequest",
            notificationRequest.getRequestId().toString(),
            Map.of(
                "totalRecipients", (Object) recipientIds.size(),
                "sentCount", (Object) sentCount,
                "skippedCount", (Object) skippedCount,
                "consentBlockedCount", (Object) consentBlockedCount
            )
        );
        
        // Outbox event
        auditHelper.writeOutbox(
            "NOTIFICATION_SEND_REQUESTED",
            "notification.send.requested.v1",
            notificationRequest.getRequestId().toString(),
            Map.of(
                "requestId", (Object) notificationRequest.getRequestId().toString(),
                "templateKey", (Object) request.templateKey(),
                "channel", (Object) request.channel(),
                "totalRecipients", (Object) recipientIds.size(),
                "sentCount", (Object) sentCount
            )
        );
        
        return new SendNotificationResponse(
            notificationRequest.getRequestId(),
            recipientIds.size(),
            sentCount,
            skippedCount + consentBlockedCount,
            dispatches
        );
    }
    
    private SendNotificationResponse.DispatchStatus dispatchToRecipientEnhanced(
        NotificationRequest request,
        String recipientId,
        String category,
        String channel,
        GetActiveTemplateResponse.LanguageVariant languageVariant,
        Map<String, String> variables
    ) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Get recipient address (simplified - in real implementation, lookup from user service)
        String recipientAddress = recipientId + "@example.com";
        
        // Perform variable substitution
        String finalSubject = substituteVariables(languageVariant.subject(), variables);
        String finalBody = substituteVariables(languageVariant.body(), variables);
        
        // Calculate message hash for idempotency
        String messageHash = calculateMessageHash(finalSubject, finalBody);
        
        // Check for duplicate message (idempotency by hash)
        Optional<NotificationMessage> existingMessage = messageRepository
            .findByTenantIdAndRecipientAddressAndMessageHash(tenantId, recipientAddress, messageHash);
        
        if (existingMessage.isPresent()) {
            logger.info("Duplicate message detected: recipientAddress={}, messageHash={}", recipientAddress, messageHash);
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "DUPLICATE",
                "Message already sent (idempotency)"
            );
        }
        
        // CONSENT ENFORCEMENT: FAIL-CLOSED with LEGAL_MANDATORY bypass
        if (consentClient != null) {
            ConsentServiceClient.ConsentCheckResult consentResult = 
                consentClient.checkConsent(tenantId, recipientId, channel, category);
            
            // If consent check FAILED (service unavailable/error)
            if (consentResult.checkFailed()) {
                logger.error("Consent check FAILED (service unavailable): recipient={}, channel={}, category={}, reason={}", 
                    recipientId, channel, category, consentResult.reason());
                
                // Check if LEGAL_MANDATORY allows bypass
                boolean allowBypass = request.getMessageCategory() == com.regulyn.notification.entity.MessageCategory.LEGAL_MANDATORY;
                
                if (!allowBypass) {
                    // FAIL-CLOSED: Block non-legal messages
                    NotificationMessage message = createMessage(
                        tenantId, request.getRequestId(), recipientId, recipientAddress,
                        channel, finalSubject, finalBody, languageVariant.format(), messageHash, userId
                    );
                    message.setStatus(NotificationMessage.MessageStatus.CONSENT_BLOCKED);
                    message.setLastErrorMessage("Consent check failed: " + consentResult.reason());
                    message.setLastErrorCode("CONSENT_CHECK_FAILED");
                    messageRepository.save(message);
                    
                    // Audit: CONSENT_CHECK_FAILED
                    auditHelper.logConsentCheckFailed(
                        request.getRequestId().toString(),
                        recipientAddress,
                        consentResult.reason(),
                        Map.of(
                            "recipientId", (Object) recipientId,
                            "channel", (Object) channel,
                            "category", (Object) category,
                            "reason", (Object) consentResult.reason()
                        )
                    );
                    
                    return new SendNotificationResponse.DispatchStatus(
                        recipientId,
                        "CONSENT_BLOCKED",
                        "Consent check unavailable: " + consentResult.reason()
                    );
                } else {
                    // LEGAL_MANDATORY: Allow bypass with audit trail
                    logger.warn("BYPASSING consent check failure for LEGAL_MANDATORY message: recipient={}, reason={}", 
                        recipientId, consentResult.reason());
                    
                    auditHelper.logBypassedConsentDueToLegal(
                        request.getRequestId().toString(),
                        recipientAddress,
                        consentResult.reason(),
                        Map.of(
                            "recipientId", (Object) recipientId,
                            "channel", (Object) channel,
                            "category", (Object) category,
                            "messageCategory", (Object) "LEGAL_MANDATORY",
                            "bypassReason", (Object) consentResult.reason()
                        )
                    );
                    // Continue to send
                }
            }
            // If NO consent (user opted out)
            else if (!consentResult.hasConsent()) {
                logger.info("Consent check: NO CONSENT - blocking send for recipient={}, channel={}, category={}", 
                    recipientId, channel, category);
                
                // Create message with CONSENT_BLOCKED status
                NotificationMessage message = createMessage(
                    tenantId, request.getRequestId(), recipientId, recipientAddress,
                    channel, finalSubject, finalBody, languageVariant.format(), messageHash, userId
                );
                message.setStatus(NotificationMessage.MessageStatus.CONSENT_BLOCKED);
                message.setLastErrorMessage("No OPT_IN consent for channel: " + channel + ", category: " + category);
                message.setLastErrorCode("NO_CONSENT");
                messageRepository.save(message);
                
                // Audit event
                auditHelper.logConsentBlocked(
                    request.getRequestId().toString(),
                    recipientAddress,
                    "NO_CONSENT",
                    Map.of(
                        "recipientId", (Object) recipientId,
                        "channel", (Object) channel,
                        "category", (Object) category
                    )
                );
                
                return new SendNotificationResponse.DispatchStatus(
                    recipientId,
                    "CONSENT_BLOCKED",
                    "No OPT_IN consent"
                );
            }
            // Has consent - proceed
        }
        
        // Check opt-out preferences (legacy)
        boolean isOptedOut = preferenceService.isOptedOut(recipientId, channel, category);
        if (isOptedOut && "MARKETING".equals(category)) {
            logger.info("User opted out: blocking MARKETING send for recipient={}", recipientId);
            
            // Log dispatch
            NotificationDispatchLog log = new NotificationDispatchLog();
            log.setTenantId(tenantId);
            log.setRequestId(request.getRequestId());
            log.setRecipientId(recipientId);
            log.setStatus("SKIPPED_OPT_OUT");
            log.setSkipReason("User opted out of " + category + " via " + channel);
            dispatchLogRepository.save(log);
            
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "SKIPPED_OPT_OUT",
                "Opted out of MARKETING"
            );
        }
        
        // Create notification message record
        NotificationMessage message = createMessage(
            tenantId, request.getRequestId(), recipientId, recipientAddress,
            channel, finalSubject, finalBody, languageVariant.format(), messageHash, userId
        );
        message.setStatus(NotificationMessage.MessageStatus.PENDING);
        message = messageRepository.save(message);
        
        // Emit QUEUED event
        auditHelper.writeOutbox(
            "NOTIFICATION_QUEUED",
            "notification.queued.v1",
            message.getMessageId().toString(),
            Map.of(
                "messageId", (Object) message.getMessageId().toString(),
                "recipientAddress", (Object) recipientAddress,
                "channel", (Object) channel
            )
        );
        
        // Find provider for channel
        NotificationProvider provider = providers.stream()
            .filter(p -> p.getChannel().equals(channel))
            .findFirst()
            .orElse(null);
        
        if (provider == null) {
            logger.error("No provider found for channel: {}", channel);
            message.setStatus(NotificationMessage.MessageStatus.FAILED_TERMINAL);
            message.setLastErrorMessage("No provider available for channel: " + channel);
            message.setLastErrorCode("NO_PROVIDER");
            messageRepository.save(message);
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "FAILED",
                "No provider for channel"
            );
        }
        
        // SEND via provider
        ProviderSendResult result = provider.send(recipientAddress, finalSubject, finalBody, languageVariant.format());
        
        if (result.success()) {
            // Success
            message.setStatus(NotificationMessage.MessageStatus.SENT);
            message.setProviderMessageId(result.providerMessageId());
            message.setProviderName(result.providerName());
            message.setAttemptCount(1);
            message.setLastAttemptAt(Instant.now());
            messageRepository.save(message);
            
            // Create initial delivery receipt
            DeliveryReceipt receipt = new DeliveryReceipt();
            receipt.setTenantId(tenantId);
            receipt.setDispatchId(message.getMessageId());
            receipt.setProviderMessageId(result.providerMessageId());
            receipt.setDeliveryStatus(DeliveryReceipt.DeliveryStatus.SENT);
            receipt.setProviderName(result.providerName());
            receipt.setProviderCallbackData(result.metadata() != null ? result.metadata().toString() : null);
            receiptRepository.save(receipt);
            
            // EVIDENCE: Create artifact
            if (evidenceClient != null) {
                String artifactId = evidenceClient.createNotificationSentArtifact(
                    tenantId,
                    message.getMessageId(),
                    recipientAddress,
                    channel,
                    finalSubject,
                    result.providerMessageId()
                );
                if (artifactId != null) {
                    message.setEvidenceArtifactId(artifactId);
                    messageRepository.save(message);
                    
                    logger.info("Evidence artifact created: artifactId={}, messageId={}", 
                        artifactId, message.getMessageId());
                }
            }
            
            // Audit event
            auditHelper.writeAudit(
                "NOTIFICATION_SENT",
                "NotificationMessage",
                message.getMessageId().toString(),
                Map.of(
                    "providerMessageId", (Object) result.providerMessageId(),
                    "providerName", (Object) result.providerName(),
                    "recipientAddress", (Object) recipientAddress
                )
            );
            
            // Outbox event
            auditHelper.writeOutbox(
                "NOTIFICATION_SENT",
                "notification.sent.v1",
                message.getMessageId().toString(),
                Map.of(
                    "messageId", (Object) message.getMessageId().toString(),
                    "providerMessageId", (Object) result.providerMessageId(),
                    "recipientAddress", (Object) recipientAddress,
                    "channel", (Object) channel,
                    "evidenceArtifactId", (Object) (message.getEvidenceArtifactId() != null ? message.getEvidenceArtifactId() : "")
                )
            );
            
            logger.info("Notification sent successfully: messageId={}, providerMessageId={}", 
                message.getMessageId(), result.providerMessageId());
            
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "SENT",
                null
            );
            
        } else {
            // Failure - schedule for retry
            message.setStatus(NotificationMessage.MessageStatus.FAILED_RETRYABLE);
            message.setAttemptCount(1);
            message.setLastAttemptAt(Instant.now());
            message.setLastErrorMessage(result.errorMessage());
            message.setLastErrorCode(result.errorCode());
            message.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(1))); // First retry after 1 minute
            messageRepository.save(message);
            
            logger.warn("Notification send failed - scheduled for retry: messageId={}, error={}", 
                message.getMessageId(), result.errorMessage());
            
            // Audit event
            auditHelper.writeAudit(
                "NOTIFICATION_FAILED",
                "NotificationMessage",
                message.getMessageId().toString(),
                Map.of(
                    "errorMessage", (Object) result.errorMessage(),
                    "errorCode", (Object) (result.errorCode() != null ? result.errorCode() : ""),
                    "status", (Object) "FAILED_RETRYABLE"
                )
            );
            
            return new SendNotificationResponse.DispatchStatus(
                recipientId,
                "QUEUED",
                "Queued for retry: " + result.errorMessage()
            );
        }
    }
    
    private NotificationMessage createMessage(
        String tenantId, UUID requestId, String recipientId, String recipientAddress,
        String channel, String subject, String body, String format, String hash, String userId
    ) {
        NotificationMessage message = new NotificationMessage();
        message.setTenantId(tenantId);
        message.setRequestId(requestId);
        message.setRecipientId(recipientId);
        message.setRecipientAddress(recipientAddress);
        message.setChannel(channel);
        message.setMessageSubject(subject);
        message.setMessageBody(body);
        message.setMessageFormat(format);
        message.setMessageHash(hash);
        message.setCreatedBy(userId);
        return message;
    }
    
    private List<String> resolveRecipients(SendNotificationRequest.Audience audience) {
        return switch (audience.type()) {
            case "BOARD" -> List.of("board-member-1", "board-member-2");
            case "ALL_USERS" -> List.of("user-1", "user-2", "user-3");
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
    
    private String calculateMessageHash(String subject, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String content = subject + body;
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
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
