package com.regulyn.notification.dto;

import java.util.UUID;

public record DeliveryCallbackResponse(
    boolean deduped,
    UUID receiptId
) {
}
