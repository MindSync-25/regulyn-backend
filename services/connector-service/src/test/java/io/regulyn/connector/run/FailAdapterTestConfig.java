package io.regulyn.connector.run;

import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.run.adapter.ConnectorAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Test-only adapter that fails deterministically for connectors with type FAIL.
 */
@TestConfiguration
@Profile("fail-adapter")
public class FailAdapterTestConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ConnectorAdapter failAdapter() {
        return new ConnectorAdapter() {
            @Override
            public boolean supports(ConnectorRun.JobType jobType, Connector connector) {
                return "FAIL".equalsIgnoreCase(connector.getConnectorType());
            }

            @Override
            public io.regulyn.connector.run.ExecutionResult executeAuditPull(ConnectorRun run, ConnectorCursorState cursorState, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                throw new RuntimeException("fail");
            }

            @Override
            public io.regulyn.connector.run.ExecutionResult executeExport(ConnectorRun run, ConnectorCursorState cursorState, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                throw new RuntimeException("fail");
            }

            @Override
            public io.regulyn.connector.run.ExecutionResult executeDelete(ConnectorRun run, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                throw new RuntimeException("fail");
            }
        };
    }
}
