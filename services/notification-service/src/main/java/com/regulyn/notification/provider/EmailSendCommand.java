package com.regulyn.notification.provider;

import java.util.UUID;

public record EmailSendCommand(
    UUID tenantId,
    UUID notificationRequestId,
    UUID notificationMessageId,
    String recipientAddress,
    String subject,
    String body,
    String format,
    String messageHashHex
) {
}