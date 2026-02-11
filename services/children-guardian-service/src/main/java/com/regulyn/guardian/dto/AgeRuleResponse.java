package com.regulyn.guardian.dto;

import java.time.Instant;
import java.util.UUID;

public record AgeRuleResponse(
        UUID ruleId,
        String countryCode,
        String stateCode,
        Short thresholdAgeYears,
        boolean isDefault,
        Short minLegalAgeYears,
        Short maxLegalAgeYears,
        Instant createdAt,
        Instant updatedAt
) {}
