package io.regulyn.identity.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalize_sorts_nested_object_keys_and_preserves_arrays() {
        Map<String, Object> innerA = new LinkedHashMap<>();
        innerA.put("b", 2);
        innerA.put("a", 1);

        Map<String, Object> innerB = new LinkedHashMap<>();
        innerB.put("a", 1);
        innerB.put("b", 2);

        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("z", innerA);
        payload1.put("list", List.of(Map.of("b", 2, "a", 1)));

        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("list", List.of(Map.of("a", 1, "b", 2)));
        payload2.put("z", innerB);

        JsonNode node1 = CanonicalJson.canonicalize(objectMapper, objectMapper.valueToTree(payload1));
        JsonNode node2 = CanonicalJson.canonicalize(objectMapper, objectMapper.valueToTree(payload2));

        String json1 = CanonicalJson.writeCanonicalJson(objectMapper, node1);
        String json2 = CanonicalJson.writeCanonicalJson(objectMapper, node2);

        assertThat(json1).isEqualTo(json2);
    }
}
