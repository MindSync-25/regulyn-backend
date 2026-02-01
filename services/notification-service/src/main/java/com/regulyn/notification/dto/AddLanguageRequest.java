package com.regulyn.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddLanguageRequest(
    @NotBlank
    String language,
    
    @NotBlank
    String subject,
    
    @NotBlank
    String body,
    
    @NotBlank
    @Pattern(regexp = "TEXT|HTML")
    String format
) {
}
