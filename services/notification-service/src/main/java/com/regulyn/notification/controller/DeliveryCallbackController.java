package com.regulyn.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.client.EvidenceServiceClient;
import com.regulyn.notification.util.NotificationAuditHelper;
import com.regulyn.notification.entity.DeliveryReceipt;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.repository.DeliveryReceiptRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for handling delivery status callbacks from email providers
 */
@RestController
@RequestMapping("/api/v1/notifications/delivery")
public class DeliveryCallbackController {
    
    private static final Logger logger = LoggerFactory.getLogger(DeliveryCallbackController.class);
    
    private final NotificationMessageRepository messageRepository;
    private final DeliveryReceiptRepository receiptRepository;
    private final NotificationAuditHelper auditHelper;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private EvidenceServiceClient evidenceClient;
    
    @Value("${notification.webhook.secret:}")
    private String webhookSecret;
    
    @Value("${notification.webhook.signature-header:X-Webhook-Signature}")
    private String signatureHeader;
    
    public DeliveryCallbackController(
        NotificationMessageRepository messageRepository,
        DeliveryReceiptRepository receiptRepository,
        NotificationAuditHelper auditHelper,
        ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.auditHelper = auditHelper;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generic delivery status update endpoint with signature verification
     * 
     * POST /api/v1/notifications/delivery/status
     * 
     * Request headers:
     * - X-Webhook-Signature: HMAC-SHA256 signature of request body (optional, if webhook.secret configured)
     * 
     * Request body:
     * {
     *   "providerMessageId": "uuid",
     *   "deliveryStatus": "DELIVERED|FAILED|BOUNCED|COMPLAINED",
     *   "providerName": "SMTP|SES|SendGrid",
     *   "eventType": "Delivery|Bounce|Complaint",
     *   "eventTime": "2024-01-01T12:00:00Z",
     *   "failureReason": "optional detailed reason",
     *   "errorMessage": "optional error",
     *   "errorCode": "optional code",
     *   "callbackData": { ... }
     * }
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, String>> updateDeliveryStatus(
        @RequestBody DeliveryStatusRequest request,
        @RequestHeader(value = "X-Webhook-Signature", required = false) String providedSignature
    ) {
        try {
            logger.info("Received delivery status update: providerMessageId={}, status={}, provider={}", 
                request.providerMessageId(), request.deliveryStatus(), request.providerName());
            
            // Verify signature if webhook secret is configured
            boolean signatureValid = verifySignature(request, providedSignature);
            if (webhookSecret != null && !webhookSecret.isBlank() && !signatureValid) {
                logger.warn("Invalid webhook signature for providerMessageId: {}", request.providerMessageId());
                return ResponseEntity.status(401).body(Map.of("status", "invalid_signature"));
            }
            
            // Find message by provider message ID
            Optional<NotificationMessage> messageOpt = messageRepository.findByProviderMessageId(request.providerMessageId());
            
            if (messageOpt.isEmpty()) {
                logger.warn("Message not found for providerMessageId: {}", request.providerMessageId());
                return ResponseEntity.ok(Map.of("status", "message_not_found"));
            }
            
            NotificationMessage message = messageOpt.get();
            String tenantId = message.getTenantId();
            
            // Calculate payload hash
            String payloadHash = calculatePayloadHash(request);
            
            // Create delivery receipt with enhanced fields
            DeliveryReceipt receipt = new DeliveryReceipt();
            receipt.setTenantId(tenantId);
            receipt.setDispatchId(message.getMessageId()); // Use message ID as dispatch ID
            receipt.setProviderMessageId(request.providerMessageId());
            receipt.setDeliveryStatus(mapToDeliveryStatus(request.deliveryStatus()));
            receipt.setProviderName(request.providerName());
            receipt.setProviderEventType(request.eventType());
            receipt.setEventTime(request.eventTime() != null ? Instant.parse(request.eventTime()) : Instant.now());
            receipt.setFailureReason(request.failureReason());
            receipt.setProviderCallbackData(request.callbackData() != null ? serializeJson(request.callbackData()) : null);
            receipt.setErrorMessage(request.errorMessage());
            receipt.setErrorCode(request.errorCode());
            receipt.setRawPayloadHash(payloadHash);
            receipt.setRawPayloadRef(serializeJson(request)); // Store full payload
            receipt.setProviderSignatureValid(signatureValid);
            
            receiptRepository.save(receipt);
            logger.debug("Created delivery receipt: receiptId={}, payloadHash={}, signatureValid={}", 
                receipt.getReceiptId(), payloadHash, signatureValid);
            
            // Update message status based on delivery status
            updateMessageStatus(message, request.deliveryStatus(), request.errorMessage(), request.errorCode());
            messageRepository.save(message);
            
            // Create evidence artifacts for DELIVERED and FAILED states
            String artifactId = createEvidenceArtifact(message, receipt, request);
            if (artifactId != null) {
                logger.info("Created evidence artifact: {}", artifactId);
                auditHelper.logReceiptArtifactStored(
                    message.getMessageId().toString(),
                    receipt.getReceiptId().toString(),
                    artifactId,
                    Map.of(
                        "deliveryStatus", (Object) request.deliveryStatus(),
                        "payloadHash", (Object) payloadHash
                    )
                );
            }
            
            // Audit event
            auditHelper.logDeliveryUpdated(
                message.getMessageId().toString(),
                message.getRecipientAddress(),
                request.deliveryStatus(),
                Map.of(
                    "providerMessageId", (Object) request.providerMessageId(),
                    "deliveryStatus", (Object) request.deliveryStatus(),
                    "providerName", (Object) request.providerName(),
                    "receiptId", (Object) receipt.getReceiptId().toString(),
                    "signatureValid", (Object) signatureValid
                )
            );
            
            logger.info("Delivery status updated: messageId={}, status={}, artifactId={}", 
                message.getMessageId(), request.deliveryStatus(), artifactId);
            
            return ResponseEntity.ok(Map.of(
                "status", "updated",
                "messageId", message.getMessageId().toString(),
                "receiptId", receipt.getReceiptId().toString()
            ));
            
        } catch (Exception e) {
            logger.error("Error updating delivery status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Verify HMAC-SHA256 signature of webhook request
     */
    private boolean verifySignature(DeliveryStatusRequest request, String providedSignature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true; // Signature verification not configured
        }
        
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        
        try {
            String payload = serializeJson(request);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);
            
            return expectedSignature.equalsIgnoreCase(providedSignature);
        } catch (Exception e) {
            logger.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Calculate SHA-256 hash of payload for evidence trail
     */
    private String calculatePayloadHash(DeliveryStatusRequest request) {
        try {
            String payload = serializeJson(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            logger.error("Error calculating payload hash: {}", e.getMessage());
            return "error";
        }
    }
    
    /**
     * Create evidence artifact for DELIVERED or FAILED notifications
     */
    private String createEvidenceArtifact(NotificationMessage message, DeliveryReceipt receipt, DeliveryStatusRequest request) {
        if (evidenceClient == null) {
            logger.debug("Evidence client not configured, skipping artifact creation");
            return null;
        }
        
        String deliveryStatus = request.deliveryStatus().toUpperCase();
        
        try {
            if ("DELIVERED".equals(deliveryStatus)) {
                // NOTIFICATION_DELIVERED artifact
                return evidenceClient.createNotificationDeliveredArtifact(
                    message.getTenantId(),
                    message.getMessageId().toString(),
                    message.getRecipientAddress(),
                    Map.of(
                        "providerMessageId", request.providerMessageId(),
                        "deliveredAt", receipt.getEventTime() != null ? receipt.getEventTime().toString() : Instant.now().toString(),
                        "providerName", request.providerName(),
                        "receiptId", receipt.getReceiptId().toString(),
                        "payloadHash", receipt.getRawPayloadHash() != null ? receipt.getRawPayloadHash() : "none"
                    )
                );
            } else if ("FAILED".equals(deliveryStatus) || "BOUNCED".equals(deliveryStatus) || "COMPLAINED".equals(deliveryStatus)) {
                // NOTIFICATION_FAILED artifact
                return evidenceClient.createNotificationFailedArtifact(
                    message.getTenantId(),
                    message.getMessageId().toString(),
                    message.getRecipientAddress(),
                    Map.of(
                        "providerMessageId", request.providerMessageId(),
                        "failedAt", receipt.getEventTime() != null ? receipt.getEventTime().toString() : Instant.now().toString(),
                        "failureReason", receipt.getFailureReason() != null ? receipt.getFailureReason() : "Unknown",
                        "deliveryStatus", deliveryStatus,
                        "errorCode", receipt.getErrorCode() != null ? receipt.getErrorCode() : "none",
                        "providerName", request.providerName(),
                        "receiptId", receipt.getReceiptId().toString(),
                        "payloadHash", receipt.getRawPayloadHash() != null ? receipt.getRawPayloadHash() : "none"
                    )
                );
            }
        } catch (Exception e) {
            logger.error("Error creating evidence artifact: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("Error serializing JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    private DeliveryReceipt.DeliveryStatus mapToDeliveryStatus(String status) {
        return switch (status.toUpperCase()) {
            case "DELIVERED" -> DeliveryReceipt.DeliveryStatus.DELIVERED;
            case "FAILED" -> DeliveryReceipt.DeliveryStatus.FAILED;
            case "BOUNCED" -> DeliveryReceipt.DeliveryStatus.BOUNCED;
            case "COMPLAINED" -> DeliveryReceipt.DeliveryStatus.COMPLAINED;
            case "SENT" -> DeliveryReceipt.DeliveryStatus.SENT;
            default -> DeliveryReceipt.DeliveryStatus.QUEUED;
        };
    }
    
    private void updateMessageStatus(NotificationMessage message, String deliveryStatus, String errorMessage, String errorCode) {
        switch (deliveryStatus.toUpperCase()) {
            case "DELIVERED" -> message.setStatus(NotificationMessage.MessageStatus.DELIVERED);
            case "FAILED", "BOUNCED", "COMPLAINED" -> {
                message.setStatus(NotificationMessage.MessageStatus.FAILED_TERMINAL);
                message.setLastErrorMessage(errorMessage);
                message.setLastErrorCode(errorCode);
            }
            case "SENT" -> message.setStatus(NotificationMessage.MessageStatus.SENT);
            default -> message.setStatus(NotificationMessage.MessageStatus.QUEUED);
        }
    }
    
    public record DeliveryStatusRequest(
        String providerMessageId,
        String deliveryStatus,
        String providerName,
        String eventType,
        String eventTime,        // ISO-8601 timestamp
        String failureReason,    // Detailed failure reason
        String errorMessage,
        String errorCode,
        Map<String, Object> callbackData
    ) {}
}
