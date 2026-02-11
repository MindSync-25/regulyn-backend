package com.regulyn.guardian.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgeRuleUpsertRequest(
        @NotBlank(message = "countryCode is required")
        @Size(min = 2, max = 2, message = "countryCode must be 2 letters")
        String countryCode,

        String stateCode,

        @NotNull(message = "thresholdAgeYears is required")
        @Min(value = 13, message = "thresholdAgeYears must be >= 13")
        @Max(value = 18, message = "thresholdAgeYears must be <= 18")
        Short thresholdAgeYears,

        Boolean isDefault
) {}
