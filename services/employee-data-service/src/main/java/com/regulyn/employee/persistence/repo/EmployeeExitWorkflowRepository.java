package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeExitWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeExitWorkflowRepository extends JpaRepository<EmployeeExitWorkflow, UUID> {
    Optional<EmployeeExitWorkflow> findByTenantIdAndEmployeeIdAndTerminatedAt(UUID tenantId, UUID employeeId, OffsetDateTime terminatedAt);
}
