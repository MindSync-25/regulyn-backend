package com.regulyn.guardian.dto;

import java.util.UUID;

public record CreateConsentResponse(
    UUID consentId,
    String status
) {}
