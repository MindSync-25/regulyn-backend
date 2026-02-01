package com.regulyn.notification.dto;

import java.util.UUID;

public record AddLanguageResponse(
    UUID languageId,
    String contentHash
) {
}
