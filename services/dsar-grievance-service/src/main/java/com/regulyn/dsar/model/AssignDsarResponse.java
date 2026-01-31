package com.regulyn.dsar.model;

import java.util.UUID;

public class AssignDsarResponse {
    
    private UUID dsarId;
    private UUID assignedTo;
    private String status;

    public AssignDsarResponse() {
    }

    public AssignDsarResponse(UUID dsarId, UUID assignedTo, String status) {
        this.dsarId = dsarId;
        this.assignedTo = assignedTo;
        this.status = status;
    }

    // Getters and setters
    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
