package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.RemediationTaskResponse;
import io.regulyn.scanner.dto.TaskEventRequest;
import io.regulyn.scanner.dto.TaskGenerationRequest;
import io.regulyn.scanner.dto.TaskGenerationResponse;
import io.regulyn.scanner.dto.TaskTransitionRequest;
import io.regulyn.scanner.model.RemediationTaskEntity;
import io.regulyn.scanner.service.TaskGenerationService;
import io.regulyn.scanner.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scanner")
public class RemediationTaskController {

    private final TaskGenerationService taskGenerationService;
    private final TaskService taskService;

    public RemediationTaskController(TaskGenerationService taskGenerationService, TaskService taskService) {
        this.taskGenerationService = taskGenerationService;
        this.taskService = taskService;
    }

    @PostMapping("/runs/{runId}/tasks/generate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TaskGenerationResponse> generateTasks(
        @PathVariable("runId") UUID runId,
        @RequestBody(required = false) TaskGenerationRequest request
    ) {
        TaskGenerationRequest finalRequest = request != null ? request : new TaskGenerationRequest();
        TaskGenerationResponse response = taskGenerationService.generateTasksForRun(runId, finalRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER', 'OPERATOR', 'AUDITOR')")
    public ResponseEntity<Page<RemediationTaskResponse>> listTasks(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "sourceId", required = false) UUID sourceId,
        @RequestParam(name = "runId", required = false) UUID runId,
        @RequestParam(name = "severity", required = false) String severity,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        RemediationTaskEntity.Status statusEnum = status != null ? RemediationTaskEntity.Status.valueOf(status) : null;
        RemediationTaskEntity.Severity severityEnum = severity != null ? RemediationTaskEntity.Severity.valueOf(severity) : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<RemediationTaskResponse> response = taskService.listTasks(statusEnum, sourceId, runId, severityEnum, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER', 'OPERATOR', 'AUDITOR')")
    public ResponseEntity<RemediationTaskResponse> getTask(@PathVariable("taskId") UUID taskId) {
        return ResponseEntity.ok(taskService.getTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/transition")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER')")
    public ResponseEntity<RemediationTaskResponse> transitionTask(
        @PathVariable("taskId") UUID taskId,
        @Valid @RequestBody TaskTransitionRequest request,
        @RequestHeader("X-User-ID") UUID userId,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        RemediationTaskResponse response = taskService.transitionTask(taskId, request, userId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{taskId}/events")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER')")
    public ResponseEntity<RemediationTaskResponse> addTaskEvent(
        @PathVariable("taskId") UUID taskId,
        @Valid @RequestBody TaskEventRequest request,
        @RequestHeader("X-User-ID") UUID userId
    ) {
        RemediationTaskResponse response = taskService.addEvent(taskId, request, userId);
        return ResponseEntity.ok(response);
    }
}
