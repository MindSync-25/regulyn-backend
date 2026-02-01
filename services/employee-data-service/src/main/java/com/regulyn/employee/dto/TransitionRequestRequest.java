package com.regulyn.employee.dto;

import com.regulyn.employee.model.EmployeeRequest;
import jakarta.validation.constraints.NotNull;

public class TransitionRequestRequest {

    @NotNull(message = "To status is required")
    private EmployeeRequest.RequestStatus toStatus;

    private String reason;

    public EmployeeRequest.RequestStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(EmployeeRequest.RequestStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
