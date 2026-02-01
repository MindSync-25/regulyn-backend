package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaActivityVersion;

import java.time.LocalDate;
import java.util.Map;

public class CreateRopaExportRequest {

    private String title;

    private LocalDate periodFrom;

    private LocalDate periodTo;

    private ExportFilters filters;

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public void setPeriodFrom(LocalDate periodFrom) {
        this.periodFrom = periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public void setPeriodTo(LocalDate periodTo) {
        this.periodTo = periodTo;
    }

    public ExportFilters getFilters() {
        return filters;
    }

    public void setFilters(ExportFilters filters) {
        this.filters = filters;
    }

    public static class ExportFilters {
        private RopaActivityVersion.Status status;
        private RopaActivityVersion.RiskLevel riskLevel;

        public RopaActivityVersion.Status getStatus() {
            return status;
        }

        public void setStatus(RopaActivityVersion.Status status) {
            this.status = status;
        }

        public RopaActivityVersion.RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RopaActivityVersion.RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }
    }
}
