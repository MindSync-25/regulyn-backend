package com.regulyn.notification.dto;

import java.util.UUID;

public record CreateVersionResponse(
    UUID versionId,
    Integer versionNumber,
    String status
) {
}
