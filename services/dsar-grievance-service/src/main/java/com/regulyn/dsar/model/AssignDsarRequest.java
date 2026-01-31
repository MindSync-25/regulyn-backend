package com.regulyn.dsar.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AssignDsarRequest {
    
    @NotNull(message = "assignedTo is required")
    private UUID assignedTo;

    // Getters and setters
    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }
}
