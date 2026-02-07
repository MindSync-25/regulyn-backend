package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionSystemExecutionRepository extends JpaRepository<DeletionSystemExecution, UUID> {

    Optional<DeletionSystemExecution> findByTenantIdAndPlanIdAndSystemKeyAndSubjectRef(UUID tenantId, UUID planId, String systemKey, String subjectRef);

    Optional<DeletionSystemExecution> findByTenantIdAndExecutionId(UUID tenantId, UUID executionId);

    List<DeletionSystemExecution> findByTenantIdAndPlanId(UUID tenantId, UUID planId);

    List<DeletionSystemExecution> findByTenantIdAndDeletionId(UUID tenantId, UUID deletionId);

    List<DeletionSystemExecution> findByTenantIdAndExecutionStatusAndNextRetryAtBefore(UUID tenantId, DeletionSystemExecutionStatus executionStatus, Instant nextRetryAt);
}
