package com.regulyn.incident.dto;

import java.util.UUID;

public record DraftNotificationResponse(
    UUID notificationId,
    String status
) {}
