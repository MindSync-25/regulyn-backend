package com.regulyn.guardian.dto;

import java.util.UUID;

public record CreateChildResponse(
    UUID childId,
    int ageYears,
    boolean isMinor,
    boolean requiresGuardianConsent
) {}
