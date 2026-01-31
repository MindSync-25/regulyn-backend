package com.regulyn.consent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateNoticeRequest(
    @NotBlank(message = "Purpose is required")
    String purpose,
    
    @NotBlank(message = "Title is required")
    String title,
    
    @NotBlank(message = "Category is required")
    String category,
    
    @NotBlank(message = "Default language is required")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format (expected: en, en-US, etc.)")
    String defaultLanguage
) {}
