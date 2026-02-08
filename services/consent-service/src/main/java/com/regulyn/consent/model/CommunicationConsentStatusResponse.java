package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record CommunicationConsentStatusResponse(
        UUID tenantId,
        UUID dataPrincipalId,
        String channel,
        String state,
        Instant effectiveAt,
        UUID ledgerId,
        String consentTextHashSha256
) {
}
