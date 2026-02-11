package com.regulyn.guardian.dto;

public record AgeRuleEffectiveResponse(
        Short thresholdAgeYears,
        String resolvedFrom
) {}
