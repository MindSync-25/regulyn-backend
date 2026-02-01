package com.regulyn.employee.dto;

import com.regulyn.employee.model.EmployeeDataRecord;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateEmployeeDataRecordRequest {

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    @NotNull(message = "Data category is required")
    private EmployeeDataRecord.DataCategory dataCategory;

    @NotNull(message = "HR purpose ID is required")
    private UUID hrPurposeId;

    private UUID systemId;

    private String notes;

    @Min(value = 0, message = "Retention days override must be non-negative")
    private Integer retentionDaysOverride;

    private Map<String, Object> metadata = new HashMap<>();

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public EmployeeDataRecord.DataCategory getDataCategory() {
        return dataCategory;
    }

    public void setDataCategory(EmployeeDataRecord.DataCategory dataCategory) {
        this.dataCategory = dataCategory;
    }

    public UUID getHrPurposeId() {
        return hrPurposeId;
    }

    public void setHrPurposeId(UUID hrPurposeId) {
        this.hrPurposeId = hrPurposeId;
    }

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getRetentionDaysOverride() {
        return retentionDaysOverride;
    }

    public void setRetentionDaysOverride(Integer retentionDaysOverride) {
        this.retentionDaysOverride = retentionDaysOverride;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
