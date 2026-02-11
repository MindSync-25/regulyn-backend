package com.regulyn.guardian.esign;

public enum ProviderId {
    STUB,
    DOCUSIGN,
    ADOBE;

    public static ProviderId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        return ProviderId.valueOf(value.trim().toUpperCase());
    }
}
