package com.regulyn.employee.repository;

import com.regulyn.employee.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<Employee> findByTenantIdAndEmployeeRef(UUID tenantId, String employeeRef);

    List<Employee> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Employee> findByTenantIdAndStatus(UUID tenantId, Employee.EmployeeStatus status);

    List<Employee> findByTenantIdAndFullNameContainingIgnoreCase(UUID tenantId, String fullName);

    List<Employee> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, Employee.EmployeeStatus status);

    List<Employee> findByTenantIdAndFullNameContainingIgnoreCaseOrderByCreatedAtDesc(UUID tenantId, String q);

    List<Employee> findByTenantIdAndStatusAndFullNameContainingIgnoreCaseOrderByCreatedAtDesc(
        UUID tenantId, Employee.EmployeeStatus status, String q);
}
