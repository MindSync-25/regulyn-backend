package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class CreateVersionResponse {

    private UUID versionId;
    private Integer versionNumber;

    public CreateVersionResponse() {
    }

    public CreateVersionResponse(UUID versionId, Integer versionNumber) {
        this.versionId = versionId;
        this.versionNumber = versionNumber;
    }

    // Getters and Setters
    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }
}
