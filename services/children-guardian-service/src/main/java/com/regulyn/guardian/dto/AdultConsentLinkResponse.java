package com.regulyn.guardian.dto;

import java.time.Instant;
import java.util.UUID;

public record AdultConsentLinkResponse(
        UUID childId,
        String consentReceiptRef,
        Instant recordedAt
) {}
