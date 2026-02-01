package com.regulyn.ropa.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public class LinkActivityRequest {

    private List<UUID> systemIds;

    private List<UUID> dataCategoryIds;

    private List<UUID> vendorIds;

    private String notes;

    // Getters and Setters
    public List<UUID> getSystemIds() {
        return systemIds;
    }

    public void setSystemIds(List<UUID> systemIds) {
        this.systemIds = systemIds;
    }

    public List<UUID> getDataCategoryIds() {
        return dataCategoryIds;
    }

    public void setDataCategoryIds(List<UUID> dataCategoryIds) {
        this.dataCategoryIds = dataCategoryIds;
    }

    public List<UUID> getVendorIds() {
        return vendorIds;
    }

    public void setVendorIds(List<UUID> vendorIds) {
        this.vendorIds = vendorIds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
