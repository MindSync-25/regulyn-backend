package com.regulyn.guardian.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateConsentRequest(
    @NotNull(message = "childId is required")
    UUID childId,
    
    @NotNull(message = "guardianId is required")
    UUID guardianId,
    
    @NotBlank(message = "purposeKey is required")
    String purposeKey,
    
    @NotBlank(message = "consentScope is required")
    String consentScope,
    
    LocalDate validFrom,
    
    LocalDate validTo,
    
    @Valid
    SignedArtifactDto signedArtifact,
    
    Boolean requiresApproval,
    
    String idempotencyKey
) {
    public record SignedArtifactDto(
        @NotBlank(message = "artifactRef is required")
        String artifactRef,
        
        @NotBlank(message = "artifactType is required")
        String artifactType,
        
        String contentHash,
        
        Instant signedAt
    ) {}
}
