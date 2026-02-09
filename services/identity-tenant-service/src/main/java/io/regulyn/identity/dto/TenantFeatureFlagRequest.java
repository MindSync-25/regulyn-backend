package io.regulyn.identity.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class TenantFeatureFlagRequest {
    private Boolean enabled;
    private JsonNode value;

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