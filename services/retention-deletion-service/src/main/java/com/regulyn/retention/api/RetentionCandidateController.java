package com.regulyn.retention.api;

import com.regulyn.retention.model.CreateRetentionCandidateRequest;
import com.regulyn.retention.model.CreateRetentionCandidateResponse;
import com.regulyn.retention.service.DeletionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/retention/candidates")
public class RetentionCandidateController {

    private final DeletionWorkflowService deletionWorkflowService;

    public RetentionCandidateController(DeletionWorkflowService deletionWorkflowService) {
        this.deletionWorkflowService = deletionWorkflowService;
    }

    @PostMapping
    public ResponseEntity<CreateRetentionCandidateResponse> createCandidate(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody CreateRetentionCandidateRequest request) {

        CreateRetentionCandidateResponse response = deletionWorkflowService.createRetentionCandidate(tenantId, request);
        return ResponseEntity.ok(response);
    }
}
