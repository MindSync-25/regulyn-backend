package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record ActiveDualNoticeResponse(
    UUID noticeId,
    UUID versionId,
    Integer versionNumber,
    String purpose,
    String region,
    LanguageSnapshot english,
    LanguageSnapshot regional,
    Instant publishedAt
) {
    public record LanguageSnapshot(
        String language,
        UUID languageTextId,
        String content,
        String contentHash
    ) {}
}
