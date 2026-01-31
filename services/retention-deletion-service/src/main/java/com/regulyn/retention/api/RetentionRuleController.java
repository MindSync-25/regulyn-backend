package com.regulyn.retention.api;

import com.regulyn.retention.model.CreateRetentionRuleRequest;
import com.regulyn.retention.model.CreateRetentionRuleResponse;
import com.regulyn.retention.service.DeletionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/retention/rules")
public class RetentionRuleController {

    private final DeletionWorkflowService deletionWorkflowService;

    public RetentionRuleController(DeletionWorkflowService deletionWorkflowService) {
        this.deletionWorkflowService = deletionWorkflowService;
    }

    @PostMapping
    public ResponseEntity<CreateRetentionRuleResponse> createRetentionRule(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @Valid @RequestBody CreateRetentionRuleRequest request) {

        CreateRetentionRuleResponse response = deletionWorkflowService.createRetentionRule(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CreateRetentionRuleResponse>> getRetentionRules(
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        List<CreateRetentionRuleResponse> rules = deletionWorkflowService.getRetentionRules(tenantId);
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/{ruleId}/disable")
    public ResponseEntity<Void> disableRetentionRule(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("ruleId") UUID ruleId) {

        deletionWorkflowService.disableRetentionRule(tenantId, userId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
