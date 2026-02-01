package com.regulyn.employee.dto;

import com.regulyn.employee.model.Employee;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EmployeeResponse {

    private UUID employeeId;
    private UUID tenantId;
    private String employeeRef;
    private String fullName;
    private String email;
    private String department;
    private Employee.EmployeeStatus status;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmployeeRef() {
        return employeeRef;
    }

    public void setEmployeeRef(String employeeRef) {
        this.employeeRef = employeeRef;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Employee.EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(Employee.EmployeeStatus status) {
        this.status = status;
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
