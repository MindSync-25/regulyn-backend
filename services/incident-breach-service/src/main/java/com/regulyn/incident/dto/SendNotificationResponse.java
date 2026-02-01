package com.regulyn.incident.dto;

import java.time.Instant;
import java.util.UUID;

public record SendNotificationResponse(
    UUID notificationId,
    String status,
    Instant sentAt
) {}
