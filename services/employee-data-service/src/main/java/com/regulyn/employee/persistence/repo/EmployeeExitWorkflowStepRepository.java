package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeExitWorkflowStep;
import com.regulyn.employee.persistence.entity.StepKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeExitWorkflowStepRepository extends JpaRepository<EmployeeExitWorkflowStep, UUID> {
    Optional<EmployeeExitWorkflowStep> findByTenantIdAndWorkflowIdAndStepKey(UUID tenantId, UUID workflowId, StepKey stepKey);
    List<EmployeeExitWorkflowStep> findByTenantIdAndWorkflowId(UUID tenantId, UUID workflowId);
}
