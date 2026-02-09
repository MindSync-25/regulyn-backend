package com.regulyn.vendor.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class VendorAccessSummaryResponseDto {

    private UUID vendorId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private long total;
    private long allowed;
    private long denied;
    private long error;
    private List<VendorAccessBySystemDto> bySystem;
    private List<VendorAccessByAccessTypeDto> byAccessType;
    private List<VendorAccessByResultDto> byResult;

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public OffsetDateTime getFrom() {
        return from;
    }

    public void setFrom(OffsetDateTime from) {
        this.from = from;
    }

    public OffsetDateTime getTo() {
        return to;
    }

    public void setTo(OffsetDateTime to) {
        this.to = to;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getAllowed() {
        return allowed;
    }

    public void setAllowed(long allowed) {
        this.allowed = allowed;
    }

    public long getDenied() {
        return denied;
    }

    public void setDenied(long denied) {
        this.denied = denied;
    }

    public long getError() {
        return error;
    }

    public void setError(long error) {
        this.error = error;
    }

    public List<VendorAccessBySystemDto> getBySystem() {
        return bySystem;
    }

    public void setBySystem(List<VendorAccessBySystemDto> bySystem) {
        this.bySystem = bySystem;
    }

    public List<VendorAccessByAccessTypeDto> getByAccessType() {
        return byAccessType;
    }

    public void setByAccessType(List<VendorAccessByAccessTypeDto> byAccessType) {
        this.byAccessType = byAccessType;
    }

    public List<VendorAccessByResultDto> getByResult() {
        return byResult;
    }

    public void setByResult(List<VendorAccessByResultDto> byResult) {
        this.byResult = byResult;
    }
}
