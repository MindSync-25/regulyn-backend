package com.regulyn.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record DeliveryCallbackRequest(
    UUID tenantId,

    @NotNull
    UUID notificationMessageId,

    @NotBlank
    String provider,

    String providerMessageId,

    @NotBlank
    @Pattern(regexp = "DELIVERED|BOUNCED|FAILED|DEFERRED|COMPLAINED|UNKNOWN")
    String status,

    Instant occurredAt,

    @Size(max = 1024)
    String failureReason,

    JsonNode rawPayload
) {
}
