package com.regulyn.employee.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.employee.persistence.entity.EmployeeResume;
import com.regulyn.employee.persistence.entity.EmployeeResumeDeletionExecutionEntity;
import com.regulyn.employee.persistence.entity.EmployeeResumeDeletionExecutionEntity.Status;
import com.regulyn.employee.persistence.repo.EmployeeResumeDeletionExecutionRepository;
import com.regulyn.employee.persistence.repo.EmployeeResumeRepository;
import com.regulyn.employee.service.EvidenceClient;
import com.regulyn.employee.service.EvidenceClient.EvidenceArtifactResponse;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ResumeRetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ResumeRetentionCleanupJob.class);

    private static final String ACTION_RESUME_DELETED = "RESUME_DELETED";
    private static final String ACTION_RESUME_DELETE_FAILED = "RESUME_DELETE_FAILED";

    private static final String EVENT_RESUME_DELETED = "resume.deleted";
    private static final String EVENT_RESUME_DELETE_FAILED = "resume.delete_failed";

    private static final String ENTITY_TYPE = "employee_resume";
    private static final String ARTIFACT_TYPE = "RESUME_DELETED_PROOF";

    private final EmployeeResumeRepository resumeRepository;
    private final EmployeeResumeDeletionExecutionRepository executionRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;
    private final int batchSize;

    public ResumeRetentionCleanupJob(
            EmployeeResumeRepository resumeRepository,
            EmployeeResumeDeletionExecutionRepository executionRepository,
            EvidenceClient evidenceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            PlatformTransactionManager transactionManager,
            @Value("${spring.application.name:employee-data-service}") String serviceName,
            @Value("${employee.resumeRetention.cleanup.batchSize:100}") int batchSize) {
        this.resumeRepository = resumeRepository;
        this.executionRepository = executionRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.serviceName = serviceName;
        this.batchSize = batchSize;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${employee.resumeRetention.cleanup.fixedDelay:PT5M}")
    public void cleanupExpiredResumes() {
        OffsetDateTime now = OffsetDateTime.now();
        Pageable pageable = PageRequest.of(0, batchSize);
        List<EmployeeResume> resumes = resumeRepository.findByStatusInAndDeleteAfterLessThanEqualOrderByDeleteAfterAsc(
                List.of("ACTIVE", "DELETION_SCHEDULED", "FAILED_RETRYABLE"),
                now,
                pageable
        );

        if (resumes.isEmpty()) {
            log.debug("No eligible resumes for retention cleanup at {}", now);
            return;
        }

        for (EmployeeResume resume : resumes) {
            UUID tenantId = resume.getTenantId();
            UUID resumeId = resume.getResumeId();
            transactionTemplate.executeWithoutResult(status -> processResume(tenantId, resumeId));
        }
    }

    private void processResume(UUID tenantId, UUID resumeId) {
        EmployeeResume resume = resumeRepository.findByTenantIdAndResumeId(tenantId, resumeId).orElse(null);
        if (resume == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (resume.getDeleteAfter() == null || resume.getDeleteAfter().isAfter(now)) {
            return;
        }

        if (!List.of("ACTIVE", "DELETION_SCHEDULED", "FAILED_RETRYABLE").contains(resume.getStatus())) {
            return;
        }

        if (executionRepository.existsByTenantIdAndResumeIdAndStatus(tenantId, resumeId, Status.STARTED)) {
            log.debug("Skipping resume {} because a deletion execution is already STARTED", resumeId);
            return;
        }

        int attemptNo = executionRepository.findTopByTenantIdAndResumeIdOrderByAttemptNoDesc(tenantId, resumeId)
                .map(existing -> existing.getAttemptNo() + 1)
                .orElse(1);

        EmployeeResumeDeletionExecutionEntity execution = new EmployeeResumeDeletionExecutionEntity();
        execution.setTenantId(tenantId);
        execution.setResumeId(resumeId);
        execution.setStatus(Status.STARTED);
        execution.setAttemptNo(attemptNo);

        try {
            execution = executionRepository.save(execution);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Deletion execution already started for resume {}", resumeId, ex);
            return;
        }

        try {
            EvidenceArtifactResponse artifact = createDeletionArtifact(resume);
            markResumeDeleted(resume, execution, artifact, now);
        } catch (ResponseStatusException ex) {
            handleEvidenceFailure(resume, execution, ex, now);
        } catch (Exception ex) {
            handleUnexpectedFailure(resume, execution, ex, now);
        }
    }

    private EvidenceArtifactResponse createDeletionArtifact(EmployeeResume resume) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resume_id", resume.getResumeId().toString());
        payload.put("tenant_id", resume.getTenantId().toString());
        if (resume.getEmployeeId() != null) {
            payload.put("employee_id", resume.getEmployeeId().toString());
        }
        if (resume.getEmployeeRef() != null) {
            payload.put("employee_ref", resume.getEmployeeRef());
        }
        payload.put("collected_at", resume.getCollectedAt().toString());
        payload.put("source", resume.getSource());
        payload.put("delete_after", resume.getDeleteAfter().toString());
        payload.put("storage_ref", resume.getStorageRef());
        payload.put("checksum_sha256", resume.getChecksumSha256());

        String json = EventJson.toCanonicalJson(payload);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String sha256 = EventHasher.sha256(json);

        String filename = "resume-deletion-" + resume.getResumeId() + ".json";
        return evidenceClient.createArtifact(
                resume.getTenantId(),
                ARTIFACT_TYPE,
                filename,
                "application/json",
                sha256,
                bytes
        );
    }

    private void markResumeDeleted(EmployeeResume resume,
                                   EmployeeResumeDeletionExecutionEntity execution,
                                   EvidenceArtifactResponse artifact,
                                   OffsetDateTime now) {
        resume.setStatus("DELETED");
        resume.setDeletedAt(now);
        resume.setDeletionArtifactRef(artifact.artifactRef());
        resume.setLastErrorCode(null);
        resume.setLastErrorMessage(null);
        resume.setUpdatedAt(now);
        resumeRepository.save(resume);

        execution.setStatus(Status.SUCCEEDED);
        execution.setFinishedAt(now);
        execution.setArtifactRef(artifact.artifactRef());
        execution.setArtifactHash(artifact.sha256());
        execution.setLastErrorCode(null);
        execution.setLastErrorMessage(null);
        executionRepository.save(execution);

        writeAuditAndOutbox(
                ACTION_RESUME_DELETED,
                EVENT_RESUME_DELETED,
                resume,
                execution,
                true,
                null,
                null
        );

        log.info("Resume {} deleted with artifact {}", resume.getResumeId(), artifact.artifactRef());
    }

    private void handleEvidenceFailure(EmployeeResume resume,
                                       EmployeeResumeDeletionExecutionEntity execution,
                                       ResponseStatusException ex,
                                       OffsetDateTime now) {
        boolean terminal = ex.getStatusCode().is4xxClientError();
        String errorCode = ex.getStatusCode().toString();
        String errorMessage = ex.getReason() != null ? ex.getReason() : "Evidence service error";

        resume.setStatus("FAILED_RETRYABLE");
        resume.setLastErrorCode(errorCode);
        resume.setLastErrorMessage(errorMessage);
        resume.setUpdatedAt(now);
        resumeRepository.save(resume);

        execution.setStatus(terminal ? Status.FAILED_TERMINAL : Status.FAILED_RETRYABLE);
        execution.setFinishedAt(now);
        execution.setLastErrorCode(errorCode);
        execution.setLastErrorMessage(errorMessage);
        executionRepository.save(execution);

        writeAuditAndOutbox(
                ACTION_RESUME_DELETE_FAILED,
                EVENT_RESUME_DELETE_FAILED,
                resume,
                execution,
                !terminal,
                errorCode,
                errorMessage
        );

        log.warn("Resume {} deletion failed (terminal={}): {}", resume.getResumeId(), terminal, errorMessage);
    }

    private void handleUnexpectedFailure(EmployeeResume resume,
                                         EmployeeResumeDeletionExecutionEntity execution,
                                         Exception ex,
                                         OffsetDateTime now) {
        String errorCode = "UNEXPECTED_ERROR";
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";

        resume.setStatus("FAILED_RETRYABLE");
        resume.setLastErrorCode(errorCode);
        resume.setLastErrorMessage(errorMessage);
        resume.setUpdatedAt(now);
        resumeRepository.save(resume);

        execution.setStatus(Status.FAILED_RETRYABLE);
        execution.setFinishedAt(now);
        execution.setLastErrorCode(errorCode);
        execution.setLastErrorMessage(errorMessage);
        executionRepository.save(execution);

        writeAuditAndOutbox(
                ACTION_RESUME_DELETE_FAILED,
                EVENT_RESUME_DELETE_FAILED,
                resume,
                execution,
                true,
                errorCode,
                errorMessage
        );

        log.error("Unexpected error during resume {} deletion", resume.getResumeId(), ex);
    }

    private void writeAuditAndOutbox(String action,
                                     String eventType,
                                     EmployeeResume resume,
                                     EmployeeResumeDeletionExecutionEntity execution,
                                     boolean retryable,
                                     String errorCode,
                                     String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resumeId", resume.getResumeId());
        payload.put("tenantId", resume.getTenantId());
        payload.put("employeeId", resume.getEmployeeId());
        payload.put("employeeRef", resume.getEmployeeRef());
        payload.put("deleteAfter", resume.getDeleteAfter());
        payload.put("source", resume.getSource());
        payload.put("storageRef", resume.getStorageRef());
        payload.put("artifactRef", resume.getDeletionArtifactRef());
        payload.put("executionId", execution.getExecutionId());
        payload.put("attemptNo", execution.getAttemptNo());
        payload.put("retryable", retryable);
        if (errorCode != null) {
            payload.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            payload.put("errorMessage", errorMessage);
        }

        JsonNode metadata = EventJson.toJsonNode(payload);
        String payloadHash = EventHasher.sha256(EventJson.toCanonicalJson(payload));

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(resume.getTenantId())
                .actorId(null)
                .actorType(AuditEvent.ActorType.SYSTEM)
                .service(serviceName)
                .action(action)
                .entityType(ENTITY_TYPE)
                .entityId(resume.getResumeId().toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(resume.getTenantId());
        envelope.setActorId(null);
        envelope.setActorType(ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType(ENTITY_TYPE);
        envelope.setEntityId(resume.getResumeId().toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(UUID.randomUUID().toString());
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }
}
