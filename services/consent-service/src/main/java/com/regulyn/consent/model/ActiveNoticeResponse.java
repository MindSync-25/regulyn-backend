package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record ActiveNoticeResponse(
    UUID noticeId,
    UUID versionId,
    Integer versionNumber,
    String purpose,
    String language,
    String content,
    String contentHash,
    Instant publishedAt
) {}
