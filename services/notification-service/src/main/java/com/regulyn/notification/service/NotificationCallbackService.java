package com.regulyn.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.notification.dto.DeliveryCallbackRequest;
import com.regulyn.notification.dto.DeliveryCallbackResponse;
import com.regulyn.notification.entity.NotificationDeliveryReceipt;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.integration.EvidenceClient;
import com.regulyn.notification.repository.NotificationDeliveryReceiptRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationCallbackService {

    private final NotificationDeliveryReceiptRepository receiptRepository;
    private final NotificationMessageRepository messageRepository;
    private final EvidenceClient evidenceClient;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public NotificationCallbackService(
        NotificationDeliveryReceiptRepository receiptRepository,
        NotificationMessageRepository messageRepository,
        EvidenceClient evidenceClient,
        ObjectMapper objectMapper,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter
    ) {
        this.receiptRepository = receiptRepository;
        this.messageRepository = messageRepository;
        this.evidenceClient = evidenceClient;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public DeliveryCallbackResponse handleSmtpEmailCallback(DeliveryCallbackRequest request) {
        UUID tenantId = resolveTenantId(request.tenantId());
        UUID messageId = request.notificationMessageId();

        ObjectNode canonicalPayload = buildCanonicalPayload(tenantId, request);
        String payloadHashHex = computePayloadHashHex(canonicalPayload);
        byte[] payloadHash = hexToBytes(payloadHashHex);

        Optional<NotificationDeliveryReceipt> existing = receiptRepository
            .findByTenantIdAndPayloadHashHex(tenantId, payloadHashHex);
        if (existing.isPresent()) {
            writeDedupedAuditAndOutbox(tenantId, messageId, request, payloadHashHex);
            return new DeliveryCallbackResponse(true, existing.get().getId());
        }

        NotificationDeliveryReceipt receipt = new NotificationDeliveryReceipt();
        receipt.setTenantId(tenantId);
        receipt.setNotificationMessageId(messageId);
        receipt.setProvider(request.provider());
        receipt.setProviderMessageId(request.providerMessageId());
        receipt.setStatus(request.status());
        receipt.setFailureReason(truncate(request.failureReason(), 1024));
        receipt.setOccurredAt(request.occurredAt());
        receipt.setPayloadHash(payloadHash);
        receipt.setPayloadHashHex(payloadHashHex);
        receipt.setReceiptPayload(canonicalPayload);

        try {
            receipt = receiptRepository.save(receipt);
        } catch (DataIntegrityViolationException ex) {
            Optional<NotificationDeliveryReceipt> deduped = receiptRepository
                .findByTenantIdAndPayloadHashHex(tenantId, payloadHashHex);
            writeDedupedAuditAndOutbox(tenantId, messageId, request, payloadHashHex);
            return new DeliveryCallbackResponse(true, deduped.map(NotificationDeliveryReceipt::getId).orElse(null));
        }

        NotificationMessage message = messageRepository
            .findByTenantIdAndId(tenantId, messageId)
            .orElseThrow(() -> new IllegalArgumentException("Notification message not found"));

        Instant receivedAt = receipt.getReceivedAt();
        Map<String, Object> deliveryMetadata = new java.util.HashMap<>();
        deliveryMetadata.put("tenant_id", tenantId.toString());
        deliveryMetadata.put("notification_message_id", message.getId().toString());
        deliveryMetadata.put("notification_request_id", message.getNotificationRequestId().toString());
        deliveryMetadata.put("provider", request.provider());
        if (request.providerMessageId() != null) {
            deliveryMetadata.put("provider_message_id", request.providerMessageId());
        }
        deliveryMetadata.put("status", request.status());
        deliveryMetadata.put("payload_hash_hex", payloadHashHex);
        if (request.occurredAt() != null) {
            deliveryMetadata.put("occurred_at", request.occurredAt().toString());
        }
        if (receivedAt != null) {
            deliveryMetadata.put("received_at", receivedAt.toString());
        }
        deliveryMetadata.put("deduped", false);
        writeAuditAndOutbox(
            "NOTIFICATION_DELIVERY_UPDATED",
            "NotificationDeliveryReceipt",
            receipt.getId().toString(),
            deliveryMetadata,
            payloadHashHex
        );

        String status = request.status();
        boolean alreadyFinal = "DELIVERED".equals(message.getStatus()) || "FAILED_TERMINAL".equals(message.getStatus());
        if ("DELIVERED".equals(status) && !alreadyFinal) {
            String deliveredEvidenceRef = evidenceClient.createNotificationDeliveredArtifact(
                buildOutcomeEvidencePayload(message, receipt, payloadHashHex, "DELIVERED"),
                tenantId + ":" + message.getId() + ":NOTIFICATION_DELIVERED"
            );
            message.setStatus("DELIVERED");
            message.setDeliveredEvidenceArtifactRef(deliveredEvidenceRef);
            messageRepository.save(message);

            Map<String, Object> deliveredMetadata = new java.util.HashMap<>();
            deliveredMetadata.put("notification_request_id", message.getNotificationRequestId().toString());
            deliveredMetadata.put("notification_message_id", message.getId().toString());
            deliveredMetadata.put("provider", request.provider());
            if (request.providerMessageId() != null) {
                deliveredMetadata.put("provider_message_id", request.providerMessageId());
            }
            deliveredMetadata.put("payload_hash_hex", payloadHashHex);
            deliveredMetadata.put("evidence_artifact_ref", deliveredEvidenceRef);
            writeAuditAndOutbox(
                "NOTIFICATION_DELIVERED",
                "NotificationMessage",
                message.getId().toString(),
                deliveredMetadata,
                message.getMessageHashHex()
            );
        } else if (isFinalFailure(status) && !alreadyFinal) {
            String failedEvidenceRef = evidenceClient.createNotificationFailedArtifact(
                buildOutcomeEvidencePayload(message, receipt, payloadHashHex, "FAILED_TERMINAL"),
                tenantId + ":" + message.getId() + ":NOTIFICATION_FAILED"
            );
            message.setStatus("FAILED_TERMINAL");
            message.setFailedEvidenceArtifactRef(failedEvidenceRef);
            message.setLastFailureReason(truncate(request.failureReason(), 1024));
            messageRepository.save(message);

            Map<String, Object> failedMetadata = new java.util.HashMap<>();
            failedMetadata.put("notification_request_id", message.getNotificationRequestId().toString());
            failedMetadata.put("notification_message_id", message.getId().toString());
            failedMetadata.put("provider", request.provider());
            if (request.providerMessageId() != null) {
                failedMetadata.put("provider_message_id", request.providerMessageId());
            }
            failedMetadata.put("payload_hash_hex", payloadHashHex);
            failedMetadata.put("evidence_artifact_ref", failedEvidenceRef);
            writeAuditAndOutbox(
                "NOTIFICATION_FAILED_TERMINAL",
                "NotificationMessage",
                message.getId().toString(),
                failedMetadata,
                message.getMessageHashHex()
            );
        }

        String receiptEvidenceRef = evidenceClient.createNotificationReceiptArtifact(
            buildReceiptEvidencePayload(message, receipt, payloadHashHex),
            tenantId + ":" + receipt.getId() + ":NOTIFICATION_RECEIPT_ARTIFACT_STORED"
        );
        receipt.setReceiptEvidenceArtifactRef(receiptEvidenceRef);
        receiptRepository.save(receipt);

        Map<String, Object> receiptMetadata = new java.util.HashMap<>();
        receiptMetadata.put("receipt_id", receipt.getId().toString());
        receiptMetadata.put("notification_message_id", message.getId().toString());
        receiptMetadata.put("notification_request_id", message.getNotificationRequestId().toString());
        receiptMetadata.put("evidence_artifact_ref", receiptEvidenceRef);
        writeAuditAndOutbox(
            "NOTIFICATION_RECEIPT_ARTIFACT_STORED",
            "NotificationDeliveryReceipt",
            receipt.getId().toString(),
            receiptMetadata,
            payloadHashHex
        );

        return new DeliveryCallbackResponse(false, receipt.getId());
    }

    private UUID resolveTenantId(UUID requestTenantId) {
        if (requestTenantId != null) {
            if (TenantContextHolder.getTenantId() == null) {
                TenantContext context = new TenantContext();
                context.setTenantId(requestTenantId);
                TenantContextHolder.setContext(context);
            }
            return requestTenantId;
        }
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("TenantId is required");
        }
        return tenantId;
    }

    private boolean isFinalFailure(String status) {
        return "BOUNCED".equals(status) || "FAILED".equals(status) || "COMPLAINED".equals(status);
    }

    private ObjectNode buildCanonicalPayload(UUID tenantId, DeliveryCallbackRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("tenantId", tenantId.toString());
        node.put("notificationMessageId", request.notificationMessageId().toString());
        node.put("provider", request.provider());
        if (request.providerMessageId() != null) {
            node.put("providerMessageId", request.providerMessageId());
        }
        node.put("status", request.status());
        if (request.occurredAt() != null) {
            node.put("occurredAt", request.occurredAt().toString());
        }
        if (request.failureReason() != null) {
            node.put("failureReason", truncate(request.failureReason(), 1024));
        }
        if (request.rawPayload() != null) {
            node.set("rawPayload", request.rawPayload());
        }
        return node;
    }

    private String computePayloadHashHex(JsonNode payload) {
        try {
            ObjectMapper sortedMapper = objectMapper.copy();
            sortedMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
            sortedMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            byte[] jsonBytes = sortedMapper.writeValueAsBytes(payload);
            return toHex(sha256Bytes(jsonBytes));
        } catch (JsonProcessingException e) {
            return toHex(sha256Bytes(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private byte[] sha256Bytes(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
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

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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

    private Map<String, Object> buildOutcomeEvidencePayload(
        NotificationMessage message,
        NotificationDeliveryReceipt receipt,
        String payloadHashHex,
        String outcomeStatus
    ) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("tenant_id", message.getTenantId().toString());
        payload.put("notification_message_id", message.getId().toString());
        payload.put("notification_request_id", message.getNotificationRequestId().toString());
        payload.put("message_hash_hex", message.getMessageHashHex());
        payload.put("provider", receipt.getProvider());
        if (receipt.getProviderMessageId() != null) {
            payload.put("provider_message_id", receipt.getProviderMessageId());
        }
        payload.put("receipt_payload_hash_hex", payloadHashHex);
        payload.put("timestamp", receipt.getOccurredAt() != null ? receipt.getOccurredAt().toString() : receipt.getReceivedAt().toString());
        payload.put("outcome_status", outcomeStatus);
        payload.put("recipient", maskEmail(message.getRecipient()));
        return payload;
    }

    private Map<String, Object> buildReceiptEvidencePayload(
        NotificationMessage message,
        NotificationDeliveryReceipt receipt,
        String payloadHashHex
    ) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("tenant_id", message.getTenantId().toString());
        payload.put("notification_message_id", message.getId().toString());
        payload.put("notification_request_id", message.getNotificationRequestId().toString());
        payload.put("receipt_id", receipt.getId().toString());
        payload.put("provider", receipt.getProvider());
        if (receipt.getProviderMessageId() != null) {
            payload.put("provider_message_id", receipt.getProviderMessageId());
        }
        payload.put("status", receipt.getStatus());
        payload.put("receipt_payload_hash_hex", payloadHashHex);
        if (receipt.getOccurredAt() != null) {
            payload.put("occurred_at", receipt.getOccurredAt().toString());
        }
        if (receipt.getReceivedAt() != null) {
            payload.put("received_at", receipt.getReceivedAt().toString());
        }
        return payload;
    }

    private void writeDedupedAuditAndOutbox(
        UUID tenantId,
        UUID messageId,
        DeliveryCallbackRequest request,
        String payloadHashHex
    ) {
        Optional<NotificationMessage> message = messageRepository.findByTenantIdAndId(tenantId, messageId);
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("tenant_id", tenantId.toString());
        metadata.put("notification_message_id", messageId.toString());
        message.map(NotificationMessage::getNotificationRequestId)
            .map(UUID::toString)
            .ifPresent(value -> metadata.put("notification_request_id", value));
        metadata.put("provider", request.provider());
        if (request.providerMessageId() != null) {
            metadata.put("provider_message_id", request.providerMessageId());
        }
        metadata.put("status", request.status());
        metadata.put("payload_hash_hex", payloadHashHex);
        metadata.put("deduped", true);
        writeAuditAndOutbox(
            "NOTIFICATION_DELIVERY_UPDATED",
            "NotificationDeliveryReceipt",
            messageId.toString(),
            metadata,
            payloadHashHex
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
            return toHex(sha256Bytes(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException e) {
            return toHex(sha256Bytes(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
}
