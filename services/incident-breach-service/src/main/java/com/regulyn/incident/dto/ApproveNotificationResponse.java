package com.regulyn.incident.dto;

import java.util.UUID;

public record ApproveNotificationResponse(
    UUID notificationId,
    String status
) {}
