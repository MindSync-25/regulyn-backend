package com.regulyn.dsar.client;

public record NotificationSendResult(
    String notificationRequestId,
    String providerMessageId
) {
}
