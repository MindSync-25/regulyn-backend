package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.notification.dto.GetActiveTemplateResponse;
import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.entity.NotificationDispatchLog;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.repository.NotificationDispatchLogRepository;
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
import java.util.*;

@Service
public class NotificationSendService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationSendService.class);
    
    private final TemplateManagementService templateService;
    private final PreferenceService preferenceService;
    private final NotificationRequestRepository requestRepository;
    private final NotificationDispatchLogRepository dispatchLogRepository;
    private final List<NotificationProvider> providers;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public NotificationSendService(
        TemplateManagementService templateService,
        PreferenceService preferenceService,
        NotificationRequestRepository requestRepository,
        NotificationDispatchLogRepository dispatchLogRepository,
        List<NotificationProvider> providers,
        ObjectMapper objectMapper,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter
    ) {
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.requestRepository = requestRepository;
        this.dispatchLogRepository = dispatchLogRepository;
        this.providers = providers;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
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
        
        // Dispatch to recipients
        List<SendNotificationResponse.DispatchStatus> dispatches = new ArrayList<>();
        int sentCount = 0;
        int skippedCount = 0;
        
        for (String recipientId : recipientIds) {
            SendNotificationResponse.DispatchStatus status = dispatchToRecipient(
                notificationRequest,
                recipientId,
                template.category(),
                request.channel(),
                languageVariant,
                request.variables()
            );
            dispatches.add(status);
            
            if ("SENT".equals(status.status())) {
                sentCount++;
            } else if ("SKIPPED_OPT_OUT".equals(status.status())) {
                skippedCount++;
            }
        }
        
        // Update counts
        notificationRequest.setSentCount(sentCount);
        notificationRequest.setSkippedCount(skippedCount);
        requestRepository.save(notificationRequest);
        
        // Audit
        auditWriter.auditAction(
            "NOTIFICATION_SEND_REQUESTED",
            "NotificationRequest",
            notificationRequest.getRequestId().toString(),
            null
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setActorId(UUID.fromString(userId));
        event.setEventType("notification.send_requested");
        event.setSourceService("notification-service");
        event.setEntityType("NotificationRequest");
        event.setEntityId(notificationRequest.getRequestId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
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
        String category,
        String channel,
        GetActiveTemplateResponse.LanguageVariant languageVariant,
        Map<String, String> variables
    ) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        // Check opt-out (MARKETING blocks, LEGAL/SECURITY allows but logs)
        boolean isOptedOut = preferenceService.isOptedOut(recipientId, channel, category);
        
        if (isOptedOut && "MARKETING".equals(category)) {
            // Block MARKETING notifications
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
        
        // Perform variable substitution
        String finalSubject = substituteVariables(languageVariant.subject(), variables);
        String finalBody = substituteVariables(languageVariant.body(), variables);
        
        // Calculate message hash
        String messageHash = calculateMessageHash(finalSubject, finalBody);
        
        // Get recipient address (simplified - in real implementation, lookup from user service)
        String recipientAddress = recipientId + "@example.com";
        
        // Find provider for channel
        NotificationProvider provider = providers.stream()
            .filter(p -> p.getChannel().equals(channel))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No provider for channel: " + channel));
        
        // Send notification
        boolean sent = provider.send(recipientAddress, finalSubject, finalBody, languageVariant.format());
        
        // Log dispatch
        NotificationDispatchLog log = new NotificationDispatchLog();
        log.setTenantId(tenantId);
        log.setRequestId(request.getRequestId());
        log.setRecipientId(recipientId);
        log.setRecipientAddress(recipientAddress);
        log.setStatus(sent ? "SENT" : "FAILED");
        log.setMessageSubject(finalSubject);
        log.setMessageBody(finalBody);
        log.setMessageHash(messageHash);
        
        if (isOptedOut && !"MARKETING".equals(category)) {
            log.setSkipReason("User opted out but " + category + " notification allowed");
        }
        
        dispatchLogRepository.save(log);
        
        // Audit individual dispatch
        if (sent) {
            auditWriter.auditAction(
                "NOTIFICATION_SENT",
                "NotificationDispatchLog",
                log.getDispatchId().toString(),
                messageHash
            );
        } else if ("SKIPPED_OPT_OUT".equals(log.getStatus())) {
            auditWriter.auditAction(
                "NOTIFICATION_SKIPPED_OPT_OUT",
                "NotificationDispatchLog",
                log.getDispatchId().toString(),
                null
            );
        } else {
            auditWriter.auditAction(
                "NOTIFICATION_FAILED",
                "NotificationDispatchLog",
                log.getDispatchId().toString(),
                null
            );
        }
        
        return new SendNotificationResponse.DispatchStatus(
            recipientId,
            sent ? "SENT" : "FAILED",
            sent ? null : "Provider failed to send"
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
