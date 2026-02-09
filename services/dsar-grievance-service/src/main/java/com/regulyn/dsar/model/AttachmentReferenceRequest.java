package com.regulyn.dsar.model;

import jakarta.validation.constraints.NotBlank;

public class AttachmentReferenceRequest {
    @NotBlank
    private String referenceValue;
    private String referenceHash;
    private String filename;
    private String contentType;

    public String getReferenceValue() {
        return referenceValue;
    }

    public void setReferenceValue(String referenceValue) {
        this.referenceValue = referenceValue;
    }

    public String getReferenceHash() {
        return referenceHash;
    }

    public void setReferenceHash(String referenceHash) {
        this.referenceHash = referenceHash;
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
}