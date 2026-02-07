package com.regulyn.retention.persistence;

import com.regulyn.retention.entity.DeletionBackupException;
import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionManualProofTask;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.entity.DeletionTombstone;
import com.regulyn.retention.enums.DeletionBackupExceptionStatus;
import com.regulyn.retention.enums.DeletionBackupExceptionType;
import com.regulyn.retention.enums.DeletionManualProofTaskStatus;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.enums.DeletionTombstoneStatus;
import com.regulyn.retention.repository.DeletionBackupExceptionRepository;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionManualProofTaskRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import com.regulyn.retention.repository.DeletionTombstoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RepositoryRoundTripTest extends AbstractPostgresIT {

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private DeletionExecutionPlanRepository deletionExecutionPlanRepository;

    @Autowired
    private DeletionSystemExecutionRepository deletionSystemExecutionRepository;

    @Autowired
    private DeletionManualProofTaskRepository deletionManualProofTaskRepository;

    @Autowired
    private DeletionBackupExceptionRepository deletionBackupExceptionRepository;

    @Autowired
    private DeletionTombstoneRepository deletionTombstoneRepository;

    @Test
    void repositoryRoundTrip() {
        UUID tenantId = UUID.randomUUID();

        DeletionRequest request = new DeletionRequest();
        request.setTenantId(tenantId);
        request.setSubjectId(UUID.randomUUID());
        request.setSubjectType("USER");
        request.setEntityType("CUSTOMER");
        request.setSource("SYSTEM");
        request.setStatus("REQUESTED");

        DeletionRequest savedRequest = deletionRequestRepository.save(request);

        DeletionExecutionPlan plan = new DeletionExecutionPlan();
        plan.setTenantId(tenantId);
        plan.setDeletionId(savedRequest.getDeletionId());
        plan.setIdempotencyKey("plan-key-1");
        plan.setPlanHashSha256("a".repeat(64));
        plan.setPlanJson("{\"steps\":[]}");

        DeletionExecutionPlan savedPlan = deletionExecutionPlanRepository.save(plan);

        DeletionSystemExecution execution = new DeletionSystemExecution();
        execution.setTenantId(tenantId);
        execution.setDeletionId(savedRequest.getDeletionId());
        execution.setPlanId(savedPlan.getPlanId());
        execution.setSystemKey("CRM");
        execution.setSubjectRef("user-123");
        execution.setExecutionStatus(DeletionSystemExecutionStatus.PENDING);

        DeletionSystemExecution savedExecution = deletionSystemExecutionRepository.save(execution);

        DeletionManualProofTask task = new DeletionManualProofTask();
        task.setTenantId(tenantId);
        task.setDeletionId(savedRequest.getDeletionId());
        task.setPlanId(savedPlan.getPlanId());
        task.setExecutionId(savedExecution.getExecutionId());
        task.setRequestedReason("Manual evidence required");
        task.setTaskStatus(DeletionManualProofTaskStatus.OPEN);

        DeletionManualProofTask savedTask = deletionManualProofTaskRepository.save(task);

        savedExecution.setManualProofTaskId(savedTask.getTaskId());
        deletionSystemExecutionRepository.save(savedExecution);

        DeletionBackupException backupException = new DeletionBackupException();
        backupException.setTenantId(tenantId);
        backupException.setDeletionId(savedRequest.getDeletionId());
        backupException.setPlanId(savedPlan.getPlanId());
        backupException.setExecutionId(savedExecution.getExecutionId());
        backupException.setExceptionType(DeletionBackupExceptionType.BACKUP_RETENTION);
        backupException.setReason("Backup retention window");
        backupException.setNotBefore(Instant.now().plusSeconds(3600));
        backupException.setStatus(DeletionBackupExceptionStatus.ACTIVE);
        backupException.setExceptionArtifactId(UUID.randomUUID());

        DeletionBackupException savedException = deletionBackupExceptionRepository.save(backupException);

        DeletionTombstone tombstone = new DeletionTombstone();
        tombstone.setTenantId(tenantId);
        tombstone.setSubjectType("USER");
        tombstone.setSubjectRef("user-123");
        tombstone.setTombstoneStatus(DeletionTombstoneStatus.ACTIVE);
        tombstone.setReason("Deletion completed");
        tombstone.setCreatedFromDeletionId(savedRequest.getDeletionId());
        tombstone.setCreatedArtifactId(UUID.randomUUID());

        DeletionTombstone savedTombstone = deletionTombstoneRepository.save(tombstone);

        Optional<DeletionExecutionPlan> planByKey = deletionExecutionPlanRepository
                .findByTenantIdAndIdempotencyKey(tenantId, "plan-key-1");
        Optional<DeletionManualProofTask> taskByExecution = deletionManualProofTaskRepository
                .findByTenantIdAndExecutionId(tenantId, savedExecution.getExecutionId());
        Optional<DeletionTombstone> tombstoneBySubject = deletionTombstoneRepository
                .findByTenantIdAndSubjectTypeAndSubjectRef(tenantId, "USER", "user-123");
        List<DeletionBackupException> exceptionsByDeletion = deletionBackupExceptionRepository
                .findByTenantIdAndDeletionId(tenantId, savedRequest.getDeletionId());

        assertThat(planByKey).isPresent();
        assertThat(taskByExecution).isPresent();
        assertThat(tombstoneBySubject).isPresent();
        assertThat(exceptionsByDeletion).hasSize(1);
        assertThat(savedPlan.getPlanId()).isNotNull();
        assertThat(savedExecution.getExecutionId()).isNotNull();
        assertThat(savedTask.getTaskId()).isNotNull();
        assertThat(savedException.getExceptionId()).isNotNull();
        assertThat(savedTombstone.getTombstoneId()).isNotNull();
    }
}
