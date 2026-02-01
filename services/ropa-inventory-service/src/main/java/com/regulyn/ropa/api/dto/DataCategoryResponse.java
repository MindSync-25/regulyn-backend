package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class DataCategoryResponse {

    private UUID dataCategoryId;

    public DataCategoryResponse() {
    }

    public DataCategoryResponse(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }

    // Getters and Setters
    public UUID getDataCategoryId() {
        return dataCategoryId;
    }

    public void setDataCategoryId(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }
}
