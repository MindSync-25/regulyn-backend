package com.regulyn.notification.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.auth.context.TenantContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Helper utility for writing audit events and outbox events in notification service
 */
@Component
public class NotificationAuditHelper {
    
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    
    public NotificationAuditHelper(
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper
    ) {
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Write audit event with metadata map
     */
    public void writeAudit(String action, String entityType, String entityId, Map<String, Object> metadata) {
        JsonNode metadataNode = metadata != null ? objectMapper.valueToTree(metadata) : null;
        auditWriter.auditAction(action, entityType, entityId, null, null, metadataNode);
    }
    
    /**
     * Write audit event without metadata
     */
    public void writeAudit(String action, String entityType, String entityId) {
        auditWriter.auditAction(action, entityType, entityId, null);
    }
    
    /**
     * Write outbox event with metadata map
     */
    public void writeOutbox(String eventType, String eventSchema, String entityId, Map<String, Object> metadata) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setTenantId(UUID.fromString(tenantId));
        envelope.setActorId(TenantContextHolder.getUserId());
        envelope.setEventType(eventSchema);
        envelope.setSourceService("notification-service");
        envelope.setEntityType("NotificationMessage");
        envelope.setEntityId(entityId);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(objectMapper.valueToTree(metadata));
        
        outboxWriter.write(envelope);
    }
    
    // Notification-specific event types
    
    public void logQueued(String requestId, String recipientEmail, String templateId, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_QUEUED", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_QUEUED", "notification.v1", requestId, metadata);
    }
    
    public void logSent(String messageId, String recipientEmail, String providerMessageId, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_SENT", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_SENT", "notification.v1", messageId, metadata);
    }
    
    public void logDeliveryUpdated(String messageId, String recipientEmail, String status, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_DELIVERY_UPDATED", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_DELIVERY_UPDATED", "notification.v1", messageId, metadata);
    }
    
    public void logFailedTerminal(String messageId, String recipientEmail, String errorMessage, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_FAILED_TERMINAL", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_FAILED_TERMINAL", "notification.v1", messageId, metadata);
    }
    
    public void logConsentBlocked(String requestId, String recipientEmail, String reason, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_CONSENT_BLOCKED", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_CONSENT_BLOCKED", "notification.v1", requestId, metadata);
    }
    
    public void logSentRetry(String messageId, String recipientEmail, int retryCount, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_SENT_RETRY", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_SENT_RETRY", "notification.v1", messageId, metadata);
    }
    
    public void logConsentCheckFailed(String requestId, String recipientEmail, String reason, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_CONSENT_CHECK_FAILED", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_CONSENT_CHECK_FAILED", "notification.v1", requestId, metadata);
    }
    
    public void logBypassedConsentDueToLegal(String requestId, String recipientEmail, String reason, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL", "NOTIFICATION_MESSAGE", recipientEmail, metadata);
        writeOutbox("NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL", "notification.v1", requestId, metadata);
    }
    
    public void logReceiptArtifactStored(String messageId, String receiptId, String artifactId, Map<String, Object> metadata) {
        writeAudit("NOTIFICATION_RECEIPT_ARTIFACT_STORED", "DELIVERY_RECEIPT", receiptId, metadata);
        writeOutbox("NOTIFICATION_RECEIPT_ARTIFACT_STORED", "notification.v1", receiptId, metadata);
    }
}
