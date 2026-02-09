package io.regulyn.identity.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static JsonNode canonicalize(ObjectMapper objectMapper, JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                sorted.set(fieldName, canonicalize(objectMapper, node.get(fieldName)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                arrayNode.add(canonicalize(objectMapper, child));
            }
            return arrayNode;
        }
        return node;
    }

    public static String writeCanonicalJson(ObjectMapper objectMapper, JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize canonical payload", ex);
        }
    }
}
