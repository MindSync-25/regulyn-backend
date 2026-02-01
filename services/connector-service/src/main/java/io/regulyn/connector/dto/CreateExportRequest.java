package io.regulyn.connector.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CreateExportRequest {

    private Instant periodFrom;
    private Instant periodTo;
    private Map<String, Object> filters = new HashMap<>();

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
