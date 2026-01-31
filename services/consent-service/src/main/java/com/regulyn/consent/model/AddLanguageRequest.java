package com.regulyn.consent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddLanguageRequest(
    @NotBlank(message = "Language is required")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format")
    String language,
    
    @NotBlank(message = "Content is required")
    String content
) {}
