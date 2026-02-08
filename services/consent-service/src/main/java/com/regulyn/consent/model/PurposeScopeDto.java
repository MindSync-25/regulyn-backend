package com.regulyn.consent.model;

import java.util.List;
import java.util.Map;

public record PurposeScopeDto(
        List<String> dataCategories,
        List<String> dataFields,
        List<String> recipients,
        String legalBasis,
        Integer retentionDays,
        List<String> processingActivities,
        Map<String, Object> extensions
) {
}
