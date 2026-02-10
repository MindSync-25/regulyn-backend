package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeResumeDeletionExecutionEntity;
import com.regulyn.employee.persistence.entity.EmployeeResumeDeletionExecutionEntity.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeResumeDeletionExecutionRepository extends JpaRepository<EmployeeResumeDeletionExecutionEntity, UUID> {
    Optional<EmployeeResumeDeletionExecutionEntity> findTopByTenantIdAndResumeIdOrderByAttemptNoDesc(UUID tenantId, UUID resumeId);
    boolean existsByTenantIdAndResumeIdAndStatus(UUID tenantId, UUID resumeId, Status status);
    List<EmployeeResumeDeletionExecutionEntity> findByTenantIdAndStatus(UUID tenantId, Status status);
}
