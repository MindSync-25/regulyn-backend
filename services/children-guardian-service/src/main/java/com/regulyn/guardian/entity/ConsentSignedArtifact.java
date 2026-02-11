package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_signed_artifacts", schema = "children")
public class ConsentSignedArtifact {
    
    @Id
    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "consent_id", nullable = false)
    private UUID consentId;
    
    @Column(name = "artifact_ref", nullable = false)
    private String artifactRef;
    
    @Column(name = "artifact_type", nullable = false)
    private String artifactType;
    
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    
    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "esign_request_id")
    private UUID esignRequestId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_envelope_id")
    private String providerEnvelopeId;

    @Column(name = "signed_payload_sha256")
    private String signedPayloadSha256;

    @Column(name = "artifact_mime")
    private String artifactMime;

    @Column(name = "artifact_stored_at")
    private Instant artifactStoredAt;

    @Column(name = "signature_verified")
    private Boolean signatureVerified;

    @Column(name = "signature_verified_at")
    private Instant signatureVerifiedAt;

    @Column(name = "signature_verification_error")
    private String signatureVerificationError;
    
    // Constructors
    public ConsentSignedArtifact() {
        this.artifactId = UUID.randomUUID();
    }
    
    // Getters and Setters
    public UUID getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }
    
    public UUID getConsentId() {
        return consentId;
    }
    
    public void setConsentId(UUID consentId) {
        this.consentId = consentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getArtifactRef() {
        return artifactRef;
    }
    
    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }
    
    public String getArtifactType() {
        return artifactType;
    }
    
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }
    
    public String getContentHash() {
        return contentHash;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    
    public Instant getSignedAt() {
        return signedAt;
    }
    
    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public UUID getEsignRequestId() {
        return esignRequestId;
    }

    public void setEsignRequestId(UUID esignRequestId) {
        this.esignRequestId = esignRequestId;
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

    public String getSignedPayloadSha256() {
        return signedPayloadSha256;
    }

    public void setSignedPayloadSha256(String signedPayloadSha256) {
        this.signedPayloadSha256 = signedPayloadSha256;
    }

    public String getArtifactMime() {
        return artifactMime;
    }

    public void setArtifactMime(String artifactMime) {
        this.artifactMime = artifactMime;
    }

    public Instant getArtifactStoredAt() {
        return artifactStoredAt;
    }

    public void setArtifactStoredAt(Instant artifactStoredAt) {
        this.artifactStoredAt = artifactStoredAt;
    }

    public Boolean getSignatureVerified() {
        return signatureVerified;
    }

    public void setSignatureVerified(Boolean signatureVerified) {
        this.signatureVerified = signatureVerified;
    }

    public Instant getSignatureVerifiedAt() {
        return signatureVerifiedAt;
    }

    public void setSignatureVerifiedAt(Instant signatureVerifiedAt) {
        this.signatureVerifiedAt = signatureVerifiedAt;
    }

    public String getSignatureVerificationError() {
        return signatureVerificationError;
    }

    public void setSignatureVerificationError(String signatureVerificationError) {
        this.signatureVerificationError = signatureVerificationError;
    }
}
