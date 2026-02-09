package com.regulyn.vendor.dto;

import java.util.List;

public class VendorAccessEventsPageResponse {

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<VendorAccessEventViewDto> items;

    public VendorAccessEventsPageResponse() {
    }

    public VendorAccessEventsPageResponse(int page, int size, long totalElements, int totalPages, List<VendorAccessEventViewDto> items) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public List<VendorAccessEventViewDto> getItems() {
        return items;
    }

    public void setItems(List<VendorAccessEventViewDto> items) {
        this.items = items;
    }
}
