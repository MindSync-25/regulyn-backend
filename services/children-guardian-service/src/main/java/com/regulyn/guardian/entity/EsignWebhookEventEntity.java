package com.regulyn.guardian.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "esign_webhook_events", schema = "children")
public class EsignWebhookEventEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_envelope_id", nullable = false, length = 120)
    private String providerEnvelopeId;

    @Column(name = "provider_event_id", nullable = false, length = 120)
    private String providerEventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "signature_header")
    private String signatureHeader;

    @Column(name = "signature_verification_status", nullable = false, length = 30)
    private String signatureVerificationStatus;

    @Column(name = "signature_verification_error")
    private String signatureVerificationError;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderEnvelopeId() {
        return providerEnvelopeId;
    }

    public void setProviderEnvelopeId(String providerEnvelopeId) {
        this.providerEnvelopeId = providerEnvelopeId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public void setPayloadSha256(String payloadSha256) {
        this.payloadSha256 = payloadSha256;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getSignatureVerificationStatus() {
        return signatureVerificationStatus;
    }

    public void setSignatureVerificationStatus(String signatureVerificationStatus) {
        this.signatureVerificationStatus = signatureVerificationStatus;
    }

    public String getSignatureVerificationError() {
        return signatureVerificationError;
    }

    public void setSignatureVerificationError(String signatureVerificationError) {
        this.signatureVerificationError = signatureVerificationError;
    }
}
