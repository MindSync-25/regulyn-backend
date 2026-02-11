package com.regulyn.nominee.dto;

import java.time.Instant;
import java.util.UUID;

public class NomineeDocumentResponse {

    private UUID nomineeDocumentId;
    private UUID tenantId;
    private UUID nomineeId;
    private UUID claimId;
    private String verificationStep;
    private String artifactRef;
    private String sha256Hash;
    private String filename;
    private String contentType;
    private long sizeBytes;
    private String sourceType;
    private Instant uploadedAt;

    public UUID getNomineeDocumentId() {
        return nomineeDocumentId;
    }

    public void setNomineeDocumentId(UUID nomineeDocumentId) {
        this.nomineeDocumentId = nomineeDocumentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public String getVerificationStep() {
        return verificationStep;
    }

    public void setVerificationStep(String verificationStep) {
        this.verificationStep = verificationStep;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
