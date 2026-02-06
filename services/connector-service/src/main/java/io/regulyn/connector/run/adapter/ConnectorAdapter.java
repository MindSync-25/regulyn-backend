package io.regulyn.connector.run.adapter;

import io.regulyn.connector.credentials.ResolvedCredentials;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.run.ExecutionResult;

/**
 * Adapter interface for executing connector runs.
 */
public interface ConnectorAdapter {

    /**
     * Whether this adapter supports the given connector and job type.
     */
    boolean supports(ConnectorRun.JobType jobType, Connector connector);

    ExecutionResult executeAuditPull(ConnectorRun run, ConnectorCursorState cursorState, ResolvedCredentials credentials);

    ExecutionResult executeExport(ConnectorRun run, ConnectorCursorState cursorState, ResolvedCredentials credentials);

    ExecutionResult executeDelete(ConnectorRun run, ResolvedCredentials credentials);
}
