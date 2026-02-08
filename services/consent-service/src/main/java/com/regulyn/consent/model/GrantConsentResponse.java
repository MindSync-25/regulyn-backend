package com.regulyn.consent.model;

import java.util.UUID;

public record GrantConsentResponse(
    UUID receiptId,
    String status,
    String receiptHash,
    UUID noticeVersionId,
    String contentHash,
    UUID purposeVersionId
) {}
