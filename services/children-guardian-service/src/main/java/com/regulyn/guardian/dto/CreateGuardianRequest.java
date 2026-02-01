package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateGuardianRequest(
    @NotNull(message = "childId is required")
    UUID childId,
    
    @NotBlank(message = "guardianName is required")
    String guardianName,
    
    String guardianEmail,
    
    String guardianPhone,
    
    @NotBlank(message = "relationship is required")
    String relationship,
    
    Boolean verificationRequired
) {}
