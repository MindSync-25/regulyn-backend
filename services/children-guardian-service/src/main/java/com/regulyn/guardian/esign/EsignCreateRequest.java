package com.regulyn.guardian.esign;

import java.util.UUID;

public record EsignCreateRequest(
        UUID tenantId,
        UUID userId,
        UUID childId,
        UUID guardianId,
        UUID consentId,
        String docType,
        Integer docVersion,
        String idempotencyKey,
        String returnUrl
) {
}
