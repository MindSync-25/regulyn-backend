package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionSystemExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeletionSystemExecutionRepository extends JpaRepository<DeletionSystemExecution, UUID> {
    
    List<DeletionSystemExecution> findByDeletionRequestId(UUID deletionRequestId);
    
    @Query("SELECT e FROM DeletionSystemExecution e WHERE e.status = 'FAILED_RETRYABLE' AND e.nextRetryAt <= :now AND e.attempts < e.maxAttempts")
    List<DeletionSystemExecution> findReadyForRetry(Instant now);
    
    @Query("SELECT COUNT(e) FROM DeletionSystemExecution e WHERE e.deletionRequestId = :deletionRequestId AND e.status NOT IN :excludedStatuses")
    long countByDeletionRequestIdAndStatusNotIn(UUID deletionRequestId, List<DeletionSystemExecution.ExecutionStatus> excludedStatuses);
}
