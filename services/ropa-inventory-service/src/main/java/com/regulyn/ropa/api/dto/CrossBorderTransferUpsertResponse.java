package com.regulyn.ropa.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CrossBorderTransferUpsertResponse {

    private UUID transferId;
    private boolean created;
    private CrossBorderTransferNaturalKey naturalKey;
    private Instant updatedAt;
    private List<UUID> dataCategoryIds;
    private List<UUID> purposeVersionIds;

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }

    public CrossBorderTransferNaturalKey getNaturalKey() {
        return naturalKey;
    }

    public void setNaturalKey(CrossBorderTransferNaturalKey naturalKey) {
        this.naturalKey = naturalKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<UUID> getDataCategoryIds() {
        return dataCategoryIds;
    }

    public void setDataCategoryIds(List<UUID> dataCategoryIds) {
        this.dataCategoryIds = dataCategoryIds;
    }

    public List<UUID> getPurposeVersionIds() {
        return purposeVersionIds;
    }

    public void setPurposeVersionIds(List<UUID> purposeVersionIds) {
        this.purposeVersionIds = purposeVersionIds;
    }
}
