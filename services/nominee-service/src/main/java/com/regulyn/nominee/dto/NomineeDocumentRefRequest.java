package com.regulyn.nominee.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class NomineeDocumentRefRequest {

    @NotBlank
    private String verificationStep;

    private UUID claimId;

    @NotBlank
    private String artifactRef;

    @NotBlank
    private String sha256Hash;

    @NotBlank
    private String filename;

    @NotBlank
    private String contentType;

    @NotNull
    @Min(0)
    private Long sizeBytes;

    private String notes;

    public String getVerificationStep() {
        return verificationStep;
    }

    public void setVerificationStep(String verificationStep) {
        this.verificationStep = verificationStep;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
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

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
