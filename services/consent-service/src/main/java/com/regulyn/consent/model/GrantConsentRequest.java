package com.regulyn.consent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record GrantConsentRequest(
    @NotNull(message = "Data principal ID is required")
    UUID dataPrincipalId,
    
    @NotBlank(message = "Purpose is required")
    String purpose,
    
    @NotBlank(message = "Language is required")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format")
    String language,
    
    @NotBlank(message = "Source is required")
    @Pattern(regexp = "^(WIDGET|PORTAL)$", message = "Source must be WIDGET or PORTAL")
    String source,

    String region,
    
    String clientRef,
    String idempotencyKey
) {}
