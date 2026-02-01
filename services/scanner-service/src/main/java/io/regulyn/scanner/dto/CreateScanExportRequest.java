package io.regulyn.scanner.dto;

import java.time.Instant;
import java.util.Map;

public class CreateScanExportRequest {

    private Instant periodFrom;
    private Instant periodTo;
    private Map<String, Object> filters;

    // Getters and Setters
    public Instant getPeriodFrom() {
        return periodFrom;
    }

    public void setPeriodFrom(Instant periodFrom) {
        this.periodFrom = periodFrom;
    }

    public Instant getPeriodTo() {
        return periodTo;
    }

    public void setPeriodTo(Instant periodTo) {
        this.periodTo = periodTo;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}
