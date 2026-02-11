package com.regulyn.guardian.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AgeEvaluationResponse(
        UUID tenantId,
        UUID childId,
        LocalDate dateOfBirth,
        String countryCode,
        String stateCode,
        short thresholdAgeYears,
        int ageYears,
        boolean isMinor,
        String resolvedFrom,
        LocalDate evaluationDate
) {}
