package io.regulyn.connector.controller;

import io.regulyn.connector.dto.CreateJobRequest;
import io.regulyn.connector.dto.JobResponse;
import io.regulyn.connector.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<JobResponse> createJob(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        JobResponse response = jobService.createJob(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{jobId}/run")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<JobResponse> runJob(@PathVariable UUID jobId) {
        JobResponse response = jobService.runJob(jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        JobResponse job = jobService.getJob(jobId);
        return ResponseEntity.ok(job);
    }

    @GetMapping
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<Page<JobResponse>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) UUID connectorId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String requestRef,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<JobResponse> jobs = jobService.listJobs(status, jobType, connectorId, subjectId, requestRef, page, size);
        return ResponseEntity.ok(jobs);
    }
}
