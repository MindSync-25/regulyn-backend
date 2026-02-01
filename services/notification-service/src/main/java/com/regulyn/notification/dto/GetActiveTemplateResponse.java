package com.regulyn.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetActiveTemplateResponse(
    UUID templateId,
    String templateKey,
    String category,
    String defaultLanguage,
    String title,
    UUID activeVersionId,
    Integer activeVersionNumber,
    List<LanguageVariant> languages
) {
    public record LanguageVariant(
        String language,
        String subject,
        String body,
        String format,
        String contentHash
    ) {
    }
}
