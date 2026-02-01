package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateSystemRequest {

    @NotBlank(message = "systemName is required")
    private String systemName;

    @NotNull(message = "systemType is required")
    private RopaSystem.SystemType systemType;

    private String ownerTeam;

    @NotNull(message = "location is required")
    private RopaSystem.Location location;

    @NotNull(message = "criticality is required")
    private RopaSystem.Criticality criticality;

    private Map<String, Object> metadata;

    // Getters and Setters
    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public RopaSystem.SystemType getSystemType() {
        return systemType;
    }

    public void setSystemType(RopaSystem.SystemType systemType) {
        this.systemType = systemType;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    public RopaSystem.Location getLocation() {
        return location;
    }

    public void setLocation(RopaSystem.Location location) {
        this.location = location;
    }

    public RopaSystem.Criticality getCriticality() {
        return criticality;
    }

    public void setCriticality(RopaSystem.Criticality criticality) {
        this.criticality = criticality;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
