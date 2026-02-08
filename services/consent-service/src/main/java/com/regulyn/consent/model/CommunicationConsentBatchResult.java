package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record CommunicationConsentBatchResult(
        UUID dataPrincipalId,
        String state,
        boolean allowed,
        String reason,
        UUID ledgerId,
        Instant effectiveAt
) {
}
