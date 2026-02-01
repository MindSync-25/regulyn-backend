package com.regulyn.employee.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignRequestRequest {

    @NotNull(message = "Assigned to is required")
    private UUID assignedTo;

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }
}
