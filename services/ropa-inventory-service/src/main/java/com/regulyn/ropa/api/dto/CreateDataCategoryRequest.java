package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaDataCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateDataCategoryRequest {

    @NotNull(message = "categoryKey is required")
    private RopaDataCategory.CategoryKey categoryKey;

    @NotBlank(message = "label is required")
    private String label;

    @NotNull(message = "sensitive is required")
    private Boolean sensitive;

    private Map<String, Object> metadata;

    // Getters and Setters
    public RopaDataCategory.CategoryKey getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(RopaDataCategory.CategoryKey categoryKey) {
        this.categoryKey = categoryKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
