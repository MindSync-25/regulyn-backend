package com.regulyn.events.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventJsonTest {

    @Test
    void toCanonicalJson_shouldProduceStableOrdering() {
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("z", "value3");
        payload1.put("a", "value1");
        payload1.put("m", "value2");
        
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("m", "value2");
        payload2.put("z", "value3");
        payload2.put("a", "value1");
        
        String json1 = EventJson.toCanonicalJson(payload1);
        String json2 = EventJson.toCanonicalJson(payload2);
        
        assertEquals(json1, json2, "Maps with same content should produce same canonical JSON regardless of insertion order");
        assertTrue(json1.indexOf("\"a\"") < json1.indexOf("\"m\""), "Keys should be alphabetically ordered");
        assertTrue(json1.indexOf("\"m\"") < json1.indexOf("\"z\""), "Keys should be alphabetically ordered");
    }

    @Test
    void toJsonNode_shouldConvertObjectToJsonNode() {
        Map<String, Object> payload = Map.of("key", "value");
        JsonNode node = EventJson.toJsonNode(payload);
        
        assertNotNull(node);
        assertEquals("value", node.get("key").asText());
    }

    @Test
    void parseJson_shouldParseJsonString() {
        String json = "{\"key\":\"value\"}";
        JsonNode node = EventJson.parseJson(json);
        
        assertNotNull(node);
        assertEquals("value", node.get("key").asText());
    }
}
