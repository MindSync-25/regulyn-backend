package com.regulyn.employee.dto;

import com.regulyn.employee.model.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateEmployeeRequest {

    @NotBlank(message = "Employee ref is required")
    private String employeeRef;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    private String department;

    @NotNull(message = "Status is required")
    private Employee.EmployeeStatus status;

    private Map<String, Object> metadata;

    // Getters and setters
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
}
