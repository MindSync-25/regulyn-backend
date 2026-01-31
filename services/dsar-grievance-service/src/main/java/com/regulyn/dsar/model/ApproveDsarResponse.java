package com.regulyn.dsar.model;

import java.util.UUID;

public class ApproveDsarResponse {
    
    private UUID dsarId;
    private boolean approved;
    private String status;

    public ApproveDsarResponse() {
    }

    public ApproveDsarResponse(UUID dsarId, boolean approved, String status) {
        this.dsarId = dsarId;
        this.approved = approved;
        this.status = status;
    }

    // Getters and setters
    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
