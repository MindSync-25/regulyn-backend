package com.regulyn.incident.client;

public record NotificationSendResult(
    String notificationRequestId,
    String providerMessageId,
    String status
) {
}
