package com.regulyn.employee.dto;

import com.regulyn.employee.model.EmployeeRequest;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateEmployeeRequestRequest {

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    @NotNull(message = "Request type is required")
    private EmployeeRequest.RequestType requestType;

    private Map<String, Object> details = new HashMap<>();

    private Boolean requiresApproval = true;

    private String idempotencyKey;

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

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
