package com.regulyn.employee.dto;

import com.regulyn.employee.model.EmployeeRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EmployeeRequestResponse {

    private UUID requestId;
    private UUID tenantId;
    private UUID employeeId;
    private EmployeeRequest.RequestType requestType;
    private EmployeeRequest.RequestStatus status;
    private Map<String, Object> detailsJson;
    private Boolean requiresApproval;
    private UUID assignedTo;
    private UUID approvedBy;
    private Instant approvedAt;
    private Instant dueAt;
    private Boolean slaBreached;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    private String closureNotes;
    private UUID evidenceBundleId;

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
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

    public EmployeeRequest.RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(EmployeeRequest.RequestType requestType) {
        this.requestType = requestType;
    }

    public EmployeeRequest.RequestStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeRequest.RequestStatus status) {
        this.status = status;
    }

    public Map<String, Object> getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(Map<String, Object> detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Boolean getSlaBreached() {
        return slaBreached;
    }

    public void setSlaBreached(Boolean slaBreached) {
        this.slaBreached = slaBreached;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }

    public UUID getEvidenceBundleId() {
        return evidenceBundleId;
    }

    public void setEvidenceBundleId(UUID evidenceBundleId) {
        this.evidenceBundleId = evidenceBundleId;
    }
}
