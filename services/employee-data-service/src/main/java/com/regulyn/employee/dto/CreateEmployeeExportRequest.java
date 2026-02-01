package com.regulyn.employee.dto;

import java.time.Instant;

public class CreateEmployeeExportRequest {

    private String title;

    private Instant periodFrom;

    private Instant periodTo;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

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
}
