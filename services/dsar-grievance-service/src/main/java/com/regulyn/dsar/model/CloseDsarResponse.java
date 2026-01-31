package com.regulyn.dsar.model;

import java.util.UUID;

public class CloseDsarResponse {
    
    private UUID dsarId;
    private String status;
    private UUID evidenceBundleId;

    public CloseDsarResponse() {
    }

    public CloseDsarResponse(UUID dsarId, String status, UUID evidenceBundleId) {
        this.dsarId = dsarId;
        this.status = status;
        this.evidenceBundleId = evidenceBundleId;
    }

    // Getters and setters
    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getEvidenceBundleId() {
        return evidenceBundleId;
    }

    public void setEvidenceBundleId(UUID evidenceBundleId) {
        this.evidenceBundleId = evidenceBundleId;
    }
}
