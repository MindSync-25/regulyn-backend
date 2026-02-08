package com.regulyn.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record NoticeReceiptRequest(
    @NotNull
    UUID tenantId,

    String notificationRequestId,

    String providerMessageId,

    @NotBlank
    String status,

    Instant occurredAt,

    String receiptRef
) {
}
