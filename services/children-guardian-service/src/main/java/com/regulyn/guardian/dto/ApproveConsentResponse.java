package com.regulyn.guardian.dto;

import java.time.Instant;
import java.util.UUID;

public record ApproveConsentResponse(
    UUID consentId,
    String status,
    Instant approvedAt
) {}
