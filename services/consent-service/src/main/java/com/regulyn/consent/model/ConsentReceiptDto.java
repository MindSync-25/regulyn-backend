package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record ConsentReceiptDto(
    UUID receiptId,
    UUID dataPrincipalId,
    String purpose,
    String source,
    String status,
    UUID noticeId,
    UUID versionId,
    Integer versionNumber,
    String language,
    String contentHash,
    String receiptHash,
    String clientRef,
    Instant grantedAt,
    Instant withdrawnAt
) {}
