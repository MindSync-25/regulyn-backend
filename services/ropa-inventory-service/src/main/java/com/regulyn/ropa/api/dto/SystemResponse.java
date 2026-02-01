package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class SystemResponse {

    private UUID systemId;
    private Boolean enabled;

    public SystemResponse() {
    }

    public SystemResponse(UUID systemId) {
        this.systemId = systemId;
    }

    public SystemResponse(UUID systemId, Boolean enabled) {
        this.systemId = systemId;
        this.enabled = enabled;
    }

    // Getters and Setters
    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
