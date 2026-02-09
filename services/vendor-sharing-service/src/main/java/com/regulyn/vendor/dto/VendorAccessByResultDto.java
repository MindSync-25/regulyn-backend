package com.regulyn.vendor.dto;

public class VendorAccessByResultDto {

    private String result;
    private long total;

    public VendorAccessByResultDto() {
    }

    public VendorAccessByResultDto(String result, long total) {
        this.result = result;
        this.total = total;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
