package com.regulyn.vendor.dto;

public class VendorAccessExportCountsDto {

    private long total;
    private long allowed;
    private long denied;
    private long error;

    public VendorAccessExportCountsDto() {
    }

    public VendorAccessExportCountsDto(long total, long allowed, long denied, long error) {
        this.total = total;
        this.allowed = allowed;
        this.denied = denied;
        this.error = error;
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
}
