package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record CommunicationConsentResponse(
        UUID ledgerId,
        boolean deduped,
        UUID tenantId,
        UUID dataPrincipalId,
        String channel,
        String state,
        Instant effectiveAt,
        Instant effectiveTimeBucket,
        String consentTextHashSha256,
        UUID noticeLanguageTextId,
        UUID evidenceArtifactId
) {
}
