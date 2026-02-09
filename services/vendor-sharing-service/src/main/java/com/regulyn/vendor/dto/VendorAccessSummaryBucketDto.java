package com.regulyn.vendor.dto;

public class VendorAccessSummaryBucketDto {

    private String key;
    private long total;

    public VendorAccessSummaryBucketDto() {
    }

    public VendorAccessSummaryBucketDto(String key, long total) {
        this.key = key;
        this.total = total;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
