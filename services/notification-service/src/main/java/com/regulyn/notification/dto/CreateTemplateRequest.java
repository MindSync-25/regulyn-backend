package com.regulyn.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTemplateRequest(
    @NotBlank
    @Pattern(regexp = "BREACH_NOTICE|DSAR_REMINDER|CONSENT_UPDATE|GENERIC")
    String templateKey,
    
    @NotBlank
    @Pattern(regexp = "LEGAL|MARKETING|SECURITY|OPERATIONS")
    String category,
    
    @NotBlank
    String defaultLanguage,
    
    @NotBlank
    String title,
    
    @NotNull
    Boolean enabled
) {
}
