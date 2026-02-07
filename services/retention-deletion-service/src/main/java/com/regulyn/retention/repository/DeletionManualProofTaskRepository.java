package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionManualProofTask;
import com.regulyn.retention.enums.DeletionManualProofTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionManualProofTaskRepository extends JpaRepository<DeletionManualProofTask, UUID> {

    Optional<DeletionManualProofTask> findByTenantIdAndExecutionId(UUID tenantId, UUID executionId);

    List<DeletionManualProofTask> findByTenantIdAndTaskStatus(UUID tenantId, DeletionManualProofTaskStatus taskStatus);
}
