package com.regulyn.dsar.model;

import java.time.Instant;
import java.util.UUID;

public class CreateDsarResponse {
    
    private UUID dsarId;
    private String status;
    private Instant dueAt;

    public CreateDsarResponse() {
    }

    public CreateDsarResponse(UUID dsarId, String status, Instant dueAt) {
        this.dsarId = dsarId;
        this.status = status;
        this.dueAt = dueAt;
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

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }
}
