package com.regulyn.vendor.dto;

import java.time.Instant;
import java.util.UUID;

public class CreateVendorExportRequest {
    private Instant fromDate;
    private Instant toDate;
    private UUID vendorId;
    private String dataCategory;

    public Instant getFromDate() { return fromDate; }
    public void setFromDate(Instant fromDate) { this.fromDate = fromDate; }

    public Instant getToDate() { return toDate; }
    public void setToDate(Instant toDate) { this.toDate = toDate; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getDataCategory() { return dataCategory; }
    public void setDataCategory(String dataCategory) { this.dataCategory = dataCategory; }
}
