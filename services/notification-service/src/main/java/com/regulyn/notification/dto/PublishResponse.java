package com.regulyn.notification.dto;

import java.util.UUID;

public record PublishResponse(
    UUID versionId,
    Integer versionNumber,
    String status
) {
}
