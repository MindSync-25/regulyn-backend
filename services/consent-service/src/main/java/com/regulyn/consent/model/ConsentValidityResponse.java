package com.regulyn.consent.model;

import java.util.UUID;

public record ConsentValidityResponse(
        UUID tenantId,
        UUID dataPrincipalId,
        String purpose,
        UUID requiredPurposeVersionId,
        boolean valid,
        String reason,
        String currentStatus
) {
}
