package io.regulyn.identity.dto;

import java.util.List;

public class EvidenceBundlePageResponse {
    private List<EvidenceBundleSummaryDTO> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;

    public List<EvidenceBundleSummaryDTO> getContent() {
        return content;
    }

    public void setContent(List<EvidenceBundleSummaryDTO> content) {
        this.content = content;
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

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
