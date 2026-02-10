package com.regulyn.ropa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CrossBorderTransferPurposeVersionId implements Serializable {

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "purpose_version_id")
    private UUID purposeVersionId;

    public CrossBorderTransferPurposeVersionId() {
    }

    public CrossBorderTransferPurposeVersionId(UUID transferId, UUID purposeVersionId) {
        this.transferId = transferId;
        this.purposeVersionId = purposeVersionId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public UUID getPurposeVersionId() {
        return purposeVersionId;
    }

    public void setPurposeVersionId(UUID purposeVersionId) {
        this.purposeVersionId = purposeVersionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossBorderTransferPurposeVersionId that = (CrossBorderTransferPurposeVersionId) o;
        return Objects.equals(transferId, that.transferId) && Objects.equals(purposeVersionId, that.purposeVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferId, purposeVersionId);
    }
}
