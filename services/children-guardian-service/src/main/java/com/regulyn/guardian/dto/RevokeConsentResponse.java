package com.regulyn.guardian.dto;

import java.time.Instant;
import java.util.UUID;

public record RevokeConsentResponse(
    UUID consentId,
    String status,
    Instant revokedAt
) {}
