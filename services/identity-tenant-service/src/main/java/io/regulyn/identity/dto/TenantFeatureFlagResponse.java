package io.regulyn.identity.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class TenantFeatureFlagResponse {
    private String flagKey;
    private Boolean enabled;
    private JsonNode value;

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = value;
    }
}