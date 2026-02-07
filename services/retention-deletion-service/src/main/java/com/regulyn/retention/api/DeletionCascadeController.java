package com.regulyn.retention.api;

import com.regulyn.retention.cascade.DeletionApprovalRequiredException;
import com.regulyn.retention.cascade.DeletionCascadeOrchestrator;
import com.regulyn.retention.cascade.DeletionClosedException;
import com.regulyn.retention.cascade.DeletionNotFoundException;
import com.regulyn.retention.cascade.IdempotencyKeyConflictException;
import com.regulyn.retention.cascade.NoSystemsConfiguredException;
import com.regulyn.retention.model.CascadeExecuteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/deletions")
public class DeletionCascadeController {

    private final DeletionCascadeOrchestrator orchestrator;

    public DeletionCascadeController(DeletionCascadeOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{deletionId}/cascade-execute")
    public ResponseEntity<?> cascadeExecute(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("deletionId") UUID deletionId) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "X-Idempotency-Key is required"));
        }

        try {
            CascadeExecuteResponse response = orchestrator.executeCascade(tenantId, userId, deletionId, idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (DeletionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (DeletionClosedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (DeletionApprovalRequiredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (NoSystemsConfiguredException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IdempotencyKeyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private static class Map {
        public static java.util.Map<String, String> of(String key, String value) {
            return java.util.Map.of(key, value);
        }
    }
}
