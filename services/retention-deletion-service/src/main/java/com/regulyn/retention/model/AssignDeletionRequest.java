package com.regulyn.retention.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AssignDeletionRequest {
    
    @NotNull(message = "Assigned to is required")
    private UUID assignedTo;
    
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
}
