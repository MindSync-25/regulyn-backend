package com.regulyn.employee.dto;

import com.regulyn.employee.model.EmployeeDataRecord;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EmployeeDataRecordResponse {

    private UUID recordId;
    private UUID tenantId;
    private UUID employeeId;
    private EmployeeDataRecord.DataCategory dataCategory;
    private UUID hrPurposeId;
    private UUID systemId;
    private String notes;
    private Integer retentionDaysOverride;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
