package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionManualProofTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeletionManualProofTaskRepository extends JpaRepository<DeletionManualProofTask, UUID> {
    
    List<DeletionManualProofTask> findByDeletionRequestId(UUID deletionRequestId);
    
    List<DeletionManualProofTask> findByTenantIdAndStatus(UUID tenantId, DeletionManualProofTask.TaskStatus status);
}
