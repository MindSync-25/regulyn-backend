package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class CrossBorderReportFilters {

    private UUID vendorId;
    private UUID systemId;
    private UUID activityId;
    private String sourceRegion;
    private String destinationRegion;
    private UUID dataCategoryId;
    private UUID purposeVersionId;

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

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

    public UUID getDataCategoryId() {
        return dataCategoryId;
    }

    public void setDataCategoryId(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }

    public UUID getPurposeVersionId() {
        return purposeVersionId;
    }

    public void setPurposeVersionId(UUID purposeVersionId) {
        this.purposeVersionId = purposeVersionId;
    }
}
