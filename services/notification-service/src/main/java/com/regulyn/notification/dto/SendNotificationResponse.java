package com.regulyn.notification.dto;

import java.util.List;
import java.util.UUID;

public record SendNotificationResponse(
    UUID notificationRequestId,
    int totalRecipients,
    int sentCount,
    int skippedCount,
    List<DispatchStatus> dispatches
) {
    public record DispatchStatus(
        String recipientId,
        String status,
        String reason
    ) {
    }
}
