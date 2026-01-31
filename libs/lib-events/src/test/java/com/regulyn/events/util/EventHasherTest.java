package com.regulyn.events.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventHasherTest {

    @Test
    void sha256_shouldProduceSameHashForSameInput() {
        String input = "test-payload";
        String hash1 = EventHasher.sha256(input);
        String hash2 = EventHasher.sha256(input);
        
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    void sha256_shouldProduceDifferentHashForDifferentInput() {
        String input1 = "test-payload-1";
        String input2 = "test-payload-2";
        
        String hash1 = EventHasher.sha256(input1);
        String hash2 = EventHasher.sha256(input2);
        
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    void sha256_shouldProduceHexEncodedHash() {
        String input = "test";
        String hash = EventHasher.sha256(input);
        
        // SHA-256 produces 64 hex characters
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash should be hex-encoded");
    }
}
