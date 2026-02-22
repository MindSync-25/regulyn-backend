package com.regulyn.consent.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurposeVersionListItemDto(
        UUID purposeVersionId,
        String purposeKey,
        Integer versionNum,
        String scopeHash,
        String legalBasis,
        Integer retentionDays,
        List<String> dataCategories,
        List<String> dataFields,
        List<String> processingActivities,
        UUID noticeId,
        UUID noticeVersionId,
        Instant createdAt
) {}
