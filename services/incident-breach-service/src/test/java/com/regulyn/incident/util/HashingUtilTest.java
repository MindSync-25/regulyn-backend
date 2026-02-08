package com.regulyn.incident.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingUtilTest {

    @Test
    void sha256Hex_isDeterministic() {
        String input = "hello-world";
        String hash1 = HashingUtil.sha256Hex(input);
        String hash2 = HashingUtil.sha256Hex(input);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256Hex_differsForDifferentInputs() {
        String hash1 = HashingUtil.sha256Hex("hello");
        String hash2 = HashingUtil.sha256Hex("hello2");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
