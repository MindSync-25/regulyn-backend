package com.regulyn.consent.model;

import java.util.UUID;

public record CommunicationConsentBatchRecipient(
        UUID dataPrincipalId,
        String userId,
        String email,
        String phone
) {
}
