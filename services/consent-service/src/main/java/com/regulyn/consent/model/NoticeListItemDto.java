package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record NoticeListItemDto(
    UUID noticeId,
    String purpose,
    String title,
    String category,
    String defaultLanguage,
    Instant createdAt,
    /** Status of the most recently created version: DRAFT, PUBLISHED, RETIRED — or null if no version yet */
    String latestVersionStatus,
    UUID latestVersionId,
    Integer latestVersionNumber
) {}
