package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;

public record ApproveConsentRequest(
    @NotBlank(message = "decision is required")
    String decision,
    
    String notes
) {}
