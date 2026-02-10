package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.TransferMechanism;

import java.util.UUID;

public class CrossBorderTransferNaturalKey {

    private UUID activityId;
    private UUID vendorId;
    private String sourceRegion;
    private String destinationRegion;
    private TransferMechanism transferMechanism;

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
}
