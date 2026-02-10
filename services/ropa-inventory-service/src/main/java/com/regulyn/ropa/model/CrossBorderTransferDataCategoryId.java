package com.regulyn.ropa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CrossBorderTransferDataCategoryId implements Serializable {

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "data_category_id")
    private UUID dataCategoryId;

    public CrossBorderTransferDataCategoryId() {
    }

    public CrossBorderTransferDataCategoryId(UUID transferId, UUID dataCategoryId) {
        this.transferId = transferId;
        this.dataCategoryId = dataCategoryId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public UUID getDataCategoryId() {
        return dataCategoryId;
    }

    public void setDataCategoryId(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossBorderTransferDataCategoryId that = (CrossBorderTransferDataCategoryId) o;
        return Objects.equals(transferId, that.transferId) && Objects.equals(dataCategoryId, that.dataCategoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferId, dataCategoryId);
    }
}
