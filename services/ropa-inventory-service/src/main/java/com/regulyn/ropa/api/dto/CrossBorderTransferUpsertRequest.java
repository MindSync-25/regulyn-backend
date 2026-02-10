package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.TransferFrequency;
import com.regulyn.ropa.model.TransferMechanism;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CrossBorderTransferUpsertRequest {

    private UUID systemId;
    private UUID activityId;
    private UUID vendorId;
    private String sourceRegion;
    private String destinationRegion;
    private TransferMechanism transferMechanism;
    private String legalBasis;
    private TransferFrequency frequency;
    private Instant startedAt;
    private Instant endedAt;
    private List<UUID> dataCategoryIds;
    private List<UUID> purposeVersionIds;

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public void setActivityId(UUID activityId) {
        this.activityId = activityId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(String sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public String getDestinationRegion() {
        return destinationRegion;
    }

    public void setDestinationRegion(String destinationRegion) {
        this.destinationRegion = destinationRegion;
    }

    public TransferMechanism getTransferMechanism() {
        return transferMechanism;
    }

    public void setTransferMechanism(TransferMechanism transferMechanism) {
        this.transferMechanism = transferMechanism;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis) {
        this.legalBasis = legalBasis;
    }

    public TransferFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(TransferFrequency frequency) {
        this.frequency = frequency;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
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
