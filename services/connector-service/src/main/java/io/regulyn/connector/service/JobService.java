package io.regulyn.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.connector.adapter.ConnectorAdapter;
import io.regulyn.connector.adapter.ConnectorExecutionResult;
import io.regulyn.connector.dto.CreateJobRequest;
import io.regulyn.connector.dto.JobResponse;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorJob;
import io.regulyn.connector.model.ConnectorJobLog;
import io.regulyn.connector.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ConnectorJobRepository jobRepository;
    private final ConnectorRepository connectorRepository;
    private final ConnectorTargetRepository targetRepository;
    private final ConnectorJobLogRepository jobLogRepository;
    private final OutboxWriter outboxWriter;
    private final Map<String, ConnectorAdapter> adapters;
    private final ObjectMapper objectMapper;

    public JobService(ConnectorJobRepository jobRepository,
                     ConnectorRepository connectorRepository,
                     ConnectorTargetRepository targetRepository,
                     ConnectorJobLogRepository jobLogRepository,
                     OutboxWriter outboxWriter,
                     Map<String, ConnectorAdapter> adapters,
                     ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.connectorRepository = connectorRepository;
        this.targetRepository = targetRepository;
        this.jobLogRepository = jobLogRepository;
        this.outboxWriter = outboxWriter;
        this.adapters = adapters;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Check idempotency
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                var existing = jobRepository.findByTenantIdAndSubjectIdAndConnectorIdAndTargetIdAndIdempotencyKey(
                        tenantId, request.getSubjectId(), request.getConnectorId(), request.getTargetId(), idempotencyKey);
                if (existing.isPresent()) {
                    log.info("Returning existing job due to idempotency key: {}", idempotencyKey);
                    return toJobResponse(existing.get());
                }
            }

            // Verify connector is ACTIVE
            Connector connector = connectorRepository.findByTenantIdAndConnectorId(tenantId, request.getConnectorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

            if (!"ACTIVE".equals(connector.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Connector is not active");
            }

            // Verify target exists and belongs to connector
            targetRepository.findByTenantIdAndTargetId(tenantId, request.getTargetId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));

            ConnectorJob job = new ConnectorJob();
            job.setTenantId(tenantId);
            job.setJobType(request.getJobType().name());
            job.setStatus("QUEUED");
            job.setConnectorId(request.getConnectorId());
            job.setTargetId(request.getTargetId());
            job.setSubjectId(request.getSubjectId());
            job.setSubjectType(request.getSubjectType().name());
            job.setRequestRef(request.getRequestRef());
            job.setIdempotencyKey(idempotencyKey);
            job.setPayload(request.getPayload());
            job.setCallbackUrl(request.getCallbackUrl());

            job = jobRepository.save(job);

            log.info("Created job {} for tenant {}", job.getJobId(), tenantId);

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("jobId", job.getJobId().toString());
            eventPayload.put("jobType", job.getJobType());
            eventPayload.put("connectorId", job.getConnectorId().toString());

            EventEnvelopeV1 event = EventFactory.create(
                    "connector.job_created",
                    "connector-service",
                    "connector_job",
                    job.getJobId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toJobResponse(job);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating job for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create job", e);
        }
    }

    @Transactional
    public JobResponse runJob(UUID jobId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            ConnectorJob job = jobRepository.findByTenantIdAndJobId(tenantId, jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            if (!"QUEUED".equals(job.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Job is not in QUEUED status");
            }

            // Get connector
            Connector connector = connectorRepository.findByTenantIdAndConnectorId(tenantId, job.getConnectorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

            // Update status to RUNNING
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            job = jobRepository.save(job);

            writeJobLog(job, "JOB_STARTED", "OK", Map.of("message", "Job execution started"));

            // Emit event
            emitJobEvent("connector.job_started", job);

            // Execute via adapter
            ConnectorAdapter adapter = adapters.get(connector.getConnectorType());
            if (adapter == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No adapter found for connector type: " + connector.getConnectorType());
            }

            ConnectorExecutionResult result;
            switch (job.getJobType()) {
                case "DELETE":
                    result = adapter.executeDelete(connector, job);
                    break;
                case "EXPORT":
                    result = adapter.executeExport(connector, job);
                    break;
                case "AUDIT_PULL":
                    result = adapter.executeAuditPull(connector, job);
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown job type: " + job.getJobType());
            }

            // Log response
            Map<String, Object> responseDetails = new HashMap<>();
            responseDetails.put("responseJson", result.getResponseJson());
            writeJobLog(job, "RESPONSE_RECEIVED", "OK", responseDetails);

            // Update job with result
            if ("SUCCEEDED".equals(result.getStatus())) {
                job.setStatus("SUCCEEDED");
                job.setResultHash(result.getResultHash());
                emitJobEvent("connector.job_succeeded", job);
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage(result.getError());
                emitJobEvent("connector.job_failed", job);
            }

            job.setFinishedAt(Instant.now());
            job = jobRepository.save(job);

            log.info("Job {} finished with status {}", jobId, job.getStatus());

            return toJobResponse(job);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error running job {} for tenant {}", jobId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to run job", e);
        }
    }

    public JobResponse getJob(UUID jobId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ConnectorJob job = jobRepository.findByTenantIdAndJobId(tenantId, jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        return toJobResponse(job);
    }

    public Page<JobResponse> listJobs(String status, String jobType, UUID connectorId,
                                     UUID subjectId, String requestRef, int page, int size) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Pageable pageable = PageRequest.of(page, size);

        try {
            Page<ConnectorJob> jobs;

            if (status != null && !status.isBlank()) {
                jobs = jobRepository.findByTenantIdAndStatusOrderByQueuedAtDesc(tenantId, status, pageable);
            } else if (jobType != null && !jobType.isBlank()) {
                jobs = jobRepository.findByTenantIdAndJobTypeOrderByQueuedAtDesc(tenantId, jobType, pageable);
            } else if (connectorId != null) {
                jobs = jobRepository.findByTenantIdAndConnectorIdOrderByQueuedAtDesc(tenantId, connectorId, pageable);
            } else if (subjectId != null) {
                jobs = jobRepository.findByTenantIdAndSubjectIdOrderByQueuedAtDesc(tenantId, subjectId, pageable);
            } else if (requestRef != null && !requestRef.isBlank()) {
                jobs = jobRepository.findByTenantIdAndRequestRefOrderByQueuedAtDesc(tenantId, requestRef, pageable);
            } else {
                jobs = jobRepository.findByTenantIdOrderByQueuedAtDesc(tenantId, pageable);
            }

            return jobs.map(this::toJobResponse);

        } catch (Exception e) {
            log.error("Error listing jobs for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list jobs", e);
        }
    }

    private void writeJobLog(ConnectorJob job, String step, String status, Map<String, Object> details) {
        ConnectorJobLog jobLog = new ConnectorJobLog();
        jobLog.setTenantId(job.getTenantId());
        jobLog.setJobId(job.getJobId());
        jobLog.setStep(step);
        jobLog.setStatus(status);
        jobLog.setDetails(details);
        jobLogRepository.save(jobLog);
    }

    private void emitJobEvent(String eventType, ConnectorJob job) {
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("jobId", job.getJobId().toString());
        eventPayload.put("status", job.getStatus());
        eventPayload.put("jobType", job.getJobType());
        if (job.getResultHash() != null) {
            eventPayload.put("resultHash", job.getResultHash());
        }

        EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "connector-service",
                "connector_job",
                job.getJobId().toString(),
                eventPayload
        );
        outboxWriter.write(event);
    }

    private JobResponse toJobResponse(ConnectorJob job) {
        JobResponse response = new JobResponse();
        response.setJobId(job.getJobId());
        response.setTenantId(job.getTenantId());
        response.setJobType(job.getJobType());
        response.setStatus(job.getStatus());
        response.setConnectorId(job.getConnectorId());
        response.setTargetId(job.getTargetId());
        response.setSubjectId(job.getSubjectId());
        response.setSubjectType(job.getSubjectType());
        response.setRequestRef(job.getRequestRef());
        response.setIdempotencyKey(job.getIdempotencyKey());
        response.setPayload(job.getPayload());
        response.setCallbackUrl(job.getCallbackUrl());
        response.setQueuedAt(job.getQueuedAt());
        response.setStartedAt(job.getStartedAt());
        response.setFinishedAt(job.getFinishedAt());
        response.setResultHash(job.getResultHash());
        response.setErrorMessage(job.getErrorMessage());
        return response;
    }
}
