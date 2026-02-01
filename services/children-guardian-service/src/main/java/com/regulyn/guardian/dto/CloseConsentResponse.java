package com.regulyn.guardian.dto;

import java.util.UUID;

public record CloseConsentResponse(
    UUID consentId,
    String status,
    UUID evidenceBundleId
) {}
