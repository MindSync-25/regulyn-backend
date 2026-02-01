package com.regulyn.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateTaskRequest(
    @NotBlank
    @Pattern(regexp = "IMPACT_ASSESSMENT|CONTAINMENT|DRAFT_NOTICE|FORENSICS|OTHER")
    String taskType,
    
    UUID assignedTo,
    String notes
) {}
