package io.regulyn.connector.run.adapter;

import io.regulyn.connector.credentials.ResolvedCredentials;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.run.ExecutionResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Default no-op adapter that simulates success.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultNoopAdapter implements ConnectorAdapter {

    @Override
    public boolean supports(ConnectorRun.JobType jobType, Connector connector) {
        return true;
    }

    @Override
    public ExecutionResult executeAuditPull(ConnectorRun run, ConnectorCursorState cursorState, ResolvedCredentials credentials) {
        Map<String, Object> cursorJson = new HashMap<>();
        cursorJson.put("lastRunId", run.getId().toString());
        cursorJson.put("ts", Instant.now().toString());

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("mode", "noop");
        receipt.put("jobType", run.getJobType().name());

        return new ExecutionResult(cursorJson, receipt);
    }

    @Override
    public ExecutionResult executeExport(ConnectorRun run, ConnectorCursorState cursorState, ResolvedCredentials credentials) {
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("mode", "noop");
        receipt.put("jobType", run.getJobType().name());

        return new ExecutionResult(null, receipt);
    }

    @Override
    public ExecutionResult executeDelete(ConnectorRun run, ResolvedCredentials credentials) {
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("mode", "noop");
        receipt.put("jobType", run.getJobType().name());

        return new ExecutionResult(null, receipt);
    }
}
