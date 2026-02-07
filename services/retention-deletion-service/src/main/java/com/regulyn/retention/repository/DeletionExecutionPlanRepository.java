package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionExecutionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionExecutionPlanRepository extends JpaRepository<DeletionExecutionPlan, UUID> {

    Optional<DeletionExecutionPlan> findByTenantIdAndDeletionIdAndPlanVersion(UUID tenantId, UUID deletionId, Integer planVersion);

    Optional<DeletionExecutionPlan> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<DeletionExecutionPlan> findByTenantIdAndDeletionIdOrderByPlanVersionDesc(UUID tenantId, UUID deletionId);
}
