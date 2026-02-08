package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record ActivePurposeVersionResponse(
        UUID purposeVersionId,
        Integer versionNum,
        String scopeHash,
        UUID noticeVersionId,
        Instant publishedAt
) {
}
