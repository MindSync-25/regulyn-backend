package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionExecutionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionExecutionPlanRepository extends JpaRepository<DeletionExecutionPlan, UUID> {
    
    Optional<DeletionExecutionPlan> findByDeletionRequestId(UUID deletionRequestId);
    
    @Query("SELECT p FROM DeletionExecutionPlan p WHERE p.tenantId = :tenantId AND p.deletionRequestId = :deletionRequestId")
    Optional<DeletionExecutionPlan> findByTenantIdAndDeletionRequestId(UUID tenantId, UUID deletionRequestId);
}
