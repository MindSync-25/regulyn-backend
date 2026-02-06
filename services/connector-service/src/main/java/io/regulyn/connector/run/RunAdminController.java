package io.regulyn.connector.run;

import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.repository.ConnectorRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Minimal admin endpoints for connector runs.
 */
@RestController
@RequestMapping("/api/v1")
public class RunAdminController {

    private final ConnectorRunRepository runRepository;

    public RunAdminController(ConnectorRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ConnectorRun> getRun(@PathVariable UUID runId) {
        ConnectorRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Run not found"));
        return ResponseEntity.ok(run);
    }

    @GetMapping("/connectors/{connectorId}/runs")
    public ResponseEntity<List<ConnectorRun>> listRuns(
            @PathVariable UUID connectorId,
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<ConnectorRun> runs = runRepository.findByConnectorIdOrderByCreatedAtDesc(
                connectorId, PageRequest.of(0, safeLimit));
        return ResponseEntity.ok(runs);
    }

    @PostMapping("/runs/{runId}/retry")
    public ResponseEntity<ConnectorRun> retryRun(@PathVariable UUID runId) {
        ConnectorRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Run not found"));

        if (run.getStatus() != ConnectorRun.RunStatus.FAILED_TERMINAL
                && run.getStatus() != ConnectorRun.RunStatus.FAILED_RETRYABLE) {
            throw new ResponseStatusException(BAD_REQUEST, "Run is not retryable");
        }

        run.setStatus(ConnectorRun.RunStatus.FAILED_RETRYABLE);
        run.setNextRetryAt(Instant.now());
        runRepository.save(run);

        return ResponseEntity.ok(run);
    }
}
