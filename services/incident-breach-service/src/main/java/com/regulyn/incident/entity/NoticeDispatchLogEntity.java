package com.regulyn.incident.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notice_dispatch_logs", schema = "incident")
public class NoticeDispatchLogEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "recipient_type", nullable = false, length = 24)
    private String recipientType;

    @Column(name = "recipient_identifier", nullable = false, length = 256)
    private String recipientIdentifier;

    @Column(name = "channel", nullable = false, length = 24)
    private String channel;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "notification_request_id", length = 120)
    private String notificationRequestId;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "dispatch_payload_sha256", nullable = false, length = 64)
    private String dispatchPayloadSha256;

    @Column(name = "receipt_ref", length = 200)
    private String receiptRef;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_status_at", nullable = false)
    private Instant lastStatusAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (queuedAt == null) {
            queuedAt = Instant.now();
        }
        if (lastStatusAt == null) {
            lastStatusAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDraftId() { return draftId; }
    public void setDraftId(UUID draftId) { this.draftId = draftId; }

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public String getRecipientIdentifier() { return recipientIdentifier; }
    public void setRecipientIdentifier(String recipientIdentifier) { this.recipientIdentifier = recipientIdentifier; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotificationRequestId() { return notificationRequestId; }
    public void setNotificationRequestId(String notificationRequestId) { this.notificationRequestId = notificationRequestId; }

    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }

    public String getDispatchPayloadSha256() { return dispatchPayloadSha256; }
    public void setDispatchPayloadSha256(String dispatchPayloadSha256) { this.dispatchPayloadSha256 = dispatchPayloadSha256; }

    public String getReceiptRef() { return receiptRef; }
    public void setReceiptRef(String receiptRef) { this.receiptRef = receiptRef; }

    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

    public Instant getLastStatusAt() { return lastStatusAt; }
    public void setLastStatusAt(Instant lastStatusAt) { this.lastStatusAt = lastStatusAt; }
}
