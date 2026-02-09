package com.regulyn.vendor.dto;

public class VendorAccessBySystemDto {

    private String systemName;
    private long total;

    public VendorAccessBySystemDto() {
    }

    public VendorAccessBySystemDto(String systemName, long total) {
        this.systemName = systemName;
        this.total = total;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
