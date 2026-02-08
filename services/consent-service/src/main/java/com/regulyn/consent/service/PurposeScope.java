package com.regulyn.consent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.consent.model.PurposeScopeDto;

import java.util.*;

class PurposeScope {

    private final Set<String> dataCategories;
    private final Set<String> dataFields;
    private final Set<String> recipients;
    private final String legalBasis;
    private final Integer retentionDays;
    private final Set<String> processingActivities;
    private final Map<String, Object> extensions;
    private final boolean provided;

    private PurposeScope(Set<String> dataCategories,
                         Set<String> dataFields,
                         Set<String> recipients,
                         String legalBasis,
                         Integer retentionDays,
                         Set<String> processingActivities,
                         Map<String, Object> extensions,
                         boolean provided) {
        this.dataCategories = dataCategories;
        this.dataFields = dataFields;
        this.recipients = recipients;
        this.legalBasis = legalBasis;
        this.retentionDays = retentionDays;
        this.processingActivities = processingActivities;
        this.extensions = extensions;
        this.provided = provided;
    }

    static PurposeScope fromDto(PurposeScopeDto dto) {
        if (dto == null) {
            return empty(false);
        }
        return new PurposeScope(
            normalizeSet(dto.dataCategories()),
            normalizeSet(dto.dataFields()),
            normalizeSet(dto.recipients()),
            normalizeString(dto.legalBasis()),
            dto.retentionDays(),
            normalizeSet(dto.processingActivities()),
            normalizeExtensions(dto.extensions()),
            true
        );
    }

    static PurposeScope fromJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return empty(true);
        }
        try {
            PurposeScopeDto dto = objectMapper.readValue(json, PurposeScopeDto.class);
            return fromDto(dto);
        } catch (Exception ex) {
            return empty(true);
        }
    }

    static PurposeScope empty(boolean provided) {
        return new PurposeScope(
            new LinkedHashSet<>(),
            new LinkedHashSet<>(),
            new LinkedHashSet<>(),
            null,
            null,
            new LinkedHashSet<>(),
            new LinkedHashMap<>(),
            provided
        );
    }

    boolean isProvided() {
        return provided;
    }

    boolean hasAnyValues() {
        return !(dataCategories.isEmpty()
            && dataFields.isEmpty()
            && recipients.isEmpty()
            && processingActivities.isEmpty()
            && (legalBasis == null || legalBasis.isBlank())
            && retentionDays == null
            && (extensions == null || extensions.isEmpty()));
    }

    Set<String> getDataCategories() {
        return dataCategories;
    }

    Set<String> getDataFields() {
        return dataFields;
    }

    Set<String> getRecipients() {
        return recipients;
    }

    String getLegalBasis() {
        return legalBasis;
    }

    Integer getRetentionDays() {
        return retentionDays;
    }

    Set<String> getProcessingActivities() {
        return processingActivities;
    }

    Map<String, Object> getExtensions() {
        return extensions;
    }

    String toCanonicalJson(ObjectMapper objectMapper) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("dataCategories", toSortedList(dataCategories));
        ordered.put("dataFields", toSortedList(dataFields));
        ordered.put("recipients", toSortedList(recipients));
        ordered.put("legalBasis", legalBasis);
        ordered.put("retentionDays", retentionDays);
        ordered.put("processingActivities", toSortedList(processingActivities));
        ordered.put("extensions", extensions != null ? new TreeMap<>(extensions) : Map.of());

        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize purpose scope", e);
        }
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = normalizeString(value);
            if (cleaned != null && !cleaned.isBlank()) {
                normalized.add(cleaned);
            }
        }
        return normalized;
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> normalizeExtensions(Map<String, Object> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(extensions);
    }

    private static List<String> toSortedList(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        list.sort(String::compareTo);
        return list;
    }
}
