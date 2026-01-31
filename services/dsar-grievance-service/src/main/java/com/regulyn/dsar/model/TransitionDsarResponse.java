package com.regulyn.dsar.model;

import java.util.UUID;

public class TransitionDsarResponse {
    
    private UUID dsarId;
    private String status;

    public TransitionDsarResponse() {
    }

    public TransitionDsarResponse(UUID dsarId, String status) {
        this.dsarId = dsarId;
        this.status = status;
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
}
