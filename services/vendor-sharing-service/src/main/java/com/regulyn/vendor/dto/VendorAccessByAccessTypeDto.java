package com.regulyn.vendor.dto;

public class VendorAccessByAccessTypeDto {

    private String accessType;
    private long total;

    public VendorAccessByAccessTypeDto() {
    }

    public VendorAccessByAccessTypeDto(String accessType, long total) {
        this.accessType = accessType;
        this.total = total;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
