package com.regulyn.consent.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record CommunicationConsentRequest(
        UUID dataPrincipalId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant effectiveAt,
        String source,
        String language,
        UUID noticeLanguageTextId,
        String consentTextHash,
        String userId,
        String email,
        String phone,
        String clientRef,
        String idempotencyKey
) {
}
