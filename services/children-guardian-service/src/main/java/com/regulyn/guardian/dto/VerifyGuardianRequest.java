package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyGuardianRequest(
    @NotBlank(message = "method is required")
    String method,
    
    String notes
) {}
