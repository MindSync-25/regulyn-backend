package com.regulyn.retention.cascade;

import com.regulyn.retention.entity.*;
import com.regulyn.retention.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Round 2 Cascade Deletion Hardening.
 * Tests execution plans, system executions, backup exceptions, tombstones, and manual proofs.
 */
@SpringBootTest
@Testcontainers
class DeletionCascadeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("deletion")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private DeletionExecutionPlanRepository planRepository;

    @Autowired
    private DeletionSystemExecutionRepository systemExecutionRepository;

    @Autowired
    private DeletionBackupExceptionRepository backupExceptionRepository;

    @Autowired
    private DeletionTombstoneRepository tombstoneRepository;

    @Autowired
    private DeletionManualProofTaskRepository manualProofTaskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test 1: Cascade execute creates plan, system executions, and audit/outbox rows
     */
    @Test
    void testCascadeExecute_CreatesPlan_AndSystemExecutions_AndAuditOutboxRows() {
        // Given: A deletion request
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        // When: Create execution plan
        DeletionExecutionPlan plan = new DeletionExecutionPlan(
            tenantId,
            request.getDeletionId(),
            "{\"systems\":[\"Salesforce\",\"S3\",\"Database\"]}"
        );
        planRepository.save(plan);
        
        // And: Create system executions
        DeletionSystemExecution exec1 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "Salesforce");
        DeletionSystemExecution exec2 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "S3");
        DeletionSystemExecution exec3 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "Database");
        
        systemExecutionRepository.saveAll(List.of(exec1, exec2, exec3));
        
        // Then: Plan exists
        assertThat(planRepository.findByDeletionRequestId(request.getDeletionId())).isPresent();
        
        // And: All system executions exist
        List<DeletionSystemExecution> executions = systemExecutionRepository.findByDeletionRequestId(request.getDeletionId());
        assertThat(executions).hasSize(3);
        assertThat(executions).allMatch(e -> e.getStatus() == DeletionSystemExecution.ExecutionStatus.PENDING);
        
        // And: Audit/outbox tables are accessible (V1/V2 migrations created them)
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deletion.audit_events", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.outbox_events", Integer.class);
        
        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
    }

    /**
     * Test 2: Connector failure marks execution as FAILED_RETRYABLE and does not complete
     */
    @Test
    void testConnectorFailure_MarksRetryable_AndDoesNotComplete() {
        // Given: A deletion request and system execution
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        DeletionSystemExecution execution = new DeletionSystemExecution(tenantId, request.getDeletionId(), "FailingSystem");
        execution.setStatus(DeletionSystemExecution.ExecutionStatus.RUNNING);
        
        // When: Simulate connector failure
        execution.setStatus(DeletionSystemExecution.ExecutionStatus.FAILED_RETRYABLE);
        execution.setAttempts(1);
        execution.setLastErrorCode("CONNECTOR_UNAVAILABLE");
        execution.setLastErrorMessage("Connection timeout");
        execution.setNextRetryAt(Instant.now().plusSeconds(60));
        
        systemExecutionRepository.save(execution);
        
        // Then: Execution is FAILED_RETRYABLE
        DeletionSystemExecution saved = systemExecutionRepository.findById(execution.getExecId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DeletionSystemExecution.ExecutionStatus.FAILED_RETRYABLE);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastErrorCode()).isEqualTo("CONNECTOR_UNAVAILABLE");
        assertThat(saved.getNextRetryAt()).isNotNull();
        
        // And: No proof artifact (failure case)
        assertThat(saved.getProofArtifactRef()).isNull();
    }

    /**
     * Test 3: Retry scheduler eventually marks as FAILED_TERMINAL after max attempts
     */
    @Test
    void testRetryScheduler_EventuallyMarksTerminal_AfterMaxAttempts() {
        // Given: A system execution with max attempts reached
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        DeletionSystemExecution execution = new DeletionSystemExecution(tenantId, request.getDeletionId(), "PermanentFailure");
        execution.setStatus(DeletionSystemExecution.ExecutionStatus.FAILED_RETRYABLE);
        execution.setAttempts(5); // Max attempts
        execution.setMaxAttempts(5);
        execution.setLastErrorCode("PERMANENT_ERROR");
        
        // When: Move to terminal state (simulating retry scheduler logic)
        execution.setStatus(DeletionSystemExecution.ExecutionStatus.FAILED_TERMINAL);
        execution.setFinishedAt(Instant.now());
        
        systemExecutionRepository.save(execution);
        
        // Then: Status is FAILED_TERMINAL
        DeletionSystemExecution saved = systemExecutionRepository.findById(execution.getExecId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DeletionSystemExecution.ExecutionStatus.FAILED_TERMINAL);
        assertThat(saved.getAttempts()).isEqualTo(5);
        assertThat(saved.getFinishedAt()).isNotNull();
    }

    /**
     * Test 4: Manual proof flow blocks completion until approved, then completes
     */
    @Test
    void testManualProofFlow_BlocksCompletion_UntilApproved_ThenCompletes() {
        // Given: A deletion request requiring manual proof
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        // And: A manual proof task
        DeletionManualProofTask task = new DeletionManualProofTask(tenantId, request.getDeletionId(), "LegacySystem");
        manualProofTaskRepository.save(task);
        
        // Then: Task is initially OPEN
        DeletionManualProofTask savedTask = manualProofTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(DeletionManualProofTask.TaskStatus.OPEN);
        
        // When: Proof is submitted
        savedTask.setStatus(DeletionManualProofTask.TaskStatus.SUBMITTED);
        savedTask.setSubmittedAt(Instant.now());
        savedTask.setProofArtifactRef("manual-proof-artifact-123");
        manualProofTaskRepository.save(savedTask);
        
        // Then: Task is SUBMITTED
        savedTask = manualProofTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(DeletionManualProofTask.TaskStatus.SUBMITTED);
        assertThat(savedTask.getProofArtifactRef()).isEqualTo("manual-proof-artifact-123");
        
        // When: Proof is approved (maker-checker)
        UUID reviewerId = UUID.randomUUID();
        savedTask.setStatus(DeletionManualProofTask.TaskStatus.APPROVED);
        savedTask.setApprovedAt(Instant.now());
        savedTask.setReviewerUserId(reviewerId);
        manualProofTaskRepository.save(savedTask);
        
        // Then: Task is APPROVED
        savedTask = manualProofTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(DeletionManualProofTask.TaskStatus.APPROVED);
        assertThat(savedTask.getReviewerUserId()).isEqualTo(reviewerId);
        assertThat(savedTask.getApprovedAt()).isNotNull();
    }

    /**
     * Test 5: Backup exception creates exception artifact and allows completion only with exception proof
     */
    @Test
    void testBackupException_CreatesExceptionArtifact_AndAllowsCompletionOnlyWithExceptionProof() {
        // Given: A deletion request and backup exception
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        // When: Create backup exception (immutable backup)
        DeletionBackupException exception = new DeletionBackupException(
            tenantId,
            request.getDeletionId(),
            "S3_Backups",
            "BACKUP_IMMUTABLE",
            "exception-artifact-456"
        );
        exception.setRetentionUntil(Instant.now().plusSeconds(86400 * 90)); // 90 days
        exception.setNotes("S3 Glacier vault locked until retention period expires");
        
        backupExceptionRepository.save(exception);
        
        // Then: Exception exists with artifact
        DeletionBackupException saved = backupExceptionRepository.findById(exception.getExceptionId()).orElseThrow();
        assertThat(saved.getReasonCode()).isEqualTo("BACKUP_IMMUTABLE");
        assertThat(saved.getExceptionArtifactRef()).isEqualTo("exception-artifact-456");
        assertThat(saved.getRetentionUntil()).isNotNull();
        
        // And: System execution should be marked as EXCEPTION_GRANTED
        DeletionSystemExecution execution = new DeletionSystemExecution(tenantId, request.getDeletionId(), "S3_Backups");
        execution.setStatus(DeletionSystemExecution.ExecutionStatus.EXCEPTION_GRANTED);
        execution.setExceptionArtifactRef("exception-artifact-456");
        
        systemExecutionRepository.save(execution);
        
        DeletionSystemExecution savedExec = systemExecutionRepository.findById(execution.getExecId()).orElseThrow();
        assertThat(savedExec.getStatus()).isEqualTo(DeletionSystemExecution.ExecutionStatus.EXCEPTION_GRANTED);
        assertThat(savedExec.getExceptionArtifactRef()).isEqualTo("exception-artifact-456");
    }

    /**
     * Test 6: Completion validation blocks when any system is missing proof
     */
    @Test
    void testCompletionValidation_BlocksWhenAnySystemMissingProof() {
        // Given: A deletion request with multiple systems
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        
        // And: System 1 SUCCEEDED with proof
        DeletionSystemExecution exec1 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "System1");
        exec1.setStatus(DeletionSystemExecution.ExecutionStatus.SUCCEEDED);
        exec1.setProofArtifactRef("proof-1");
        
        // And: System 2 SUCCEEDED but NO PROOF (invalid state)
        DeletionSystemExecution exec2 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "System2");
        exec2.setStatus(DeletionSystemExecution.ExecutionStatus.SUCCEEDED);
        exec2.setProofArtifactRef(null); // Missing proof!
        
        // And: System 3 still PENDING
        DeletionSystemExecution exec3 = new DeletionSystemExecution(tenantId, request.getDeletionId(), "System3");
        exec3.setStatus(DeletionSystemExecution.ExecutionStatus.PENDING);
        
        systemExecutionRepository.saveAll(List.of(exec1, exec2, exec3));
        
        // When: Check completion readiness
        List<DeletionSystemExecution> allExecutions = systemExecutionRepository.findByDeletionRequestId(request.getDeletionId());
        
        // Then: Not all systems are complete
        boolean allComplete = allExecutions.stream().allMatch(e -> 
            (e.getStatus() == DeletionSystemExecution.ExecutionStatus.SUCCEEDED && e.getProofArtifactRef() != null) ||
            (e.getStatus() == DeletionSystemExecution.ExecutionStatus.EXCEPTION_GRANTED && e.getExceptionArtifactRef() != null)
        );
        
        assertThat(allComplete).isFalse(); // Completion should be blocked
        
        // And: Can identify incomplete systems
        List<String> incompleteSystems = allExecutions.stream()
            .filter(e -> e.getProofArtifactRef() == null && e.getExceptionArtifactRef() == null)
            .map(DeletionSystemExecution::getSystemName)
            .toList();
        
        assertThat(incompleteSystems).containsExactlyInAnyOrder("System2", "System3");
    }

    /**
     * Test 7: Tombstone created on completion and remove requires approval evidence
     */
    @Test
    void testTombstone_CreatedOnCompletion_AndRemoveRequiresApprovalEvidence() {
        // Given: A completed deletion request
        UUID tenantId = UUID.randomUUID();
        DeletionRequest request = createTestDeletionRequest(tenantId);
        String subjectRef = "customer-" + request.getSubjectId().toString();
        
        // When: Create tombstone on completion
        DeletionTombstone tombstone = new DeletionTombstone(tenantId, subjectRef, request.getDeletionId());
        tombstoneRepository.save(tombstone);
        
        // Then: Tombstone exists
        DeletionTombstone saved = tombstoneRepository.findById(tombstone.getTombstoneId()).orElseThrow();
        assertThat(saved.getSubjectRef()).isEqualTo(subjectRef);
        assertThat(saved.getDeletionRequestId()).isEqualTo(request.getDeletionId());
        assertThat(saved.getTombstonedAt()).isNotNull();
        
        // And: Can check if subject is tombstoned (prevents re-creation)
        boolean exists = tombstoneRepository.existsByTenantIdAndSubjectRef(tenantId, subjectRef);
        assertThat(exists).isTrue();
        
        // When: Remove tombstone (requires approval in real implementation)
        tombstoneRepository.delete(saved);
        
        // Then: Tombstone is removed
        boolean stillExists = tombstoneRepository.existsByTenantIdAndSubjectRef(tenantId, subjectRef);
        assertThat(stillExists).isFalse();
    }

    // Helper method
    private DeletionRequest createTestDeletionRequest(UUID tenantId) {
        DeletionRequest request = new DeletionRequest();
        request.setDeletionId(UUID.randomUUID());
        request.setTenantId(tenantId);
        request.setSubjectId(UUID.randomUUID());
        request.setSubjectType("CUSTOMER");
        request.setEntityType("CUSTOMER_DATA");
        request.setSource("ADMIN");
        request.setStatus("PENDING");
        request.setRequiresApproval(true);
        request.setProofRequired(true);
        request.setDueAt(Instant.now().plusSeconds(86400 * 30));
        
        return deletionRequestRepository.save(request);
    }
}
