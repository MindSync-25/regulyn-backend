package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record ReconsentRequirementListItemDto(
        UUID id,
        UUID dataPrincipalId,
        String purposeKey,
        UUID noticeId,
        UUID requiredPurposeVersionId,
        String status,
        Instant createdAt,
        Instant satisfiedAt,
        UUID satisfiedByConsentReceiptId
) {}
