package io.regulyn.connector;

import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import io.regulyn.connector.repository.ConnectorRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Round 2 connector_credentials and connector_runs tables.
 */
class ConnectorRound2IntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConnectorCredentialRepository credentialRepository;

    @Autowired
    private ConnectorRunRepository runRepository;

    // Helper method to create a connector entity (parent for FK)
    private UUID createTestConnector(UUID tenantId) {
        UUID connectorId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO connector.connectors (connector_id, tenant_id, connector_name, connector_type, status, auth_type, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, NOW(), NOW())",
            connectorId, tenantId, "Test Connector", "REST_API", "ACTIVE", "API_KEY"
        );
        return connectorId;
    }

    @Test
    void testRound2TablesExist() {
        // Verify connector_credentials table exists
        Integer credTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'connector' AND table_name = 'connector_credentials'",
            Integer.class
        );
        assertEquals(1, credTableCount, "connector_credentials table should exist");

        // Verify connector_runs table exists
        Integer runTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'connector' AND table_name = 'connector_runs'",
            Integer.class
        );
        assertEquals(1, runTableCount, "connector_runs table should exist");
    }

    @Test
    void testCredentialProviderConstraint() {
        // Valid providers should work
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);
        
        ConnectorCredential validCred = new ConnectorCredential();
        validCred.setTenantId(tenantId);
        validCred.setConnectorId(connectorId);
        validCred.setProvider(ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
        ConnectorCredential saved = credentialRepository.save(validCred);
        assertNotNull(saved.getId());

        // Invalid provider should fail (test via direct SQL)
        UUID tenantId2 = UUID.randomUUID();
        UUID connectorId2 = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO connector.connector_credentials (id, tenant_id, connector_id, provider, created_at, updated_at) VALUES (?, ?, ?, 'INVALID_PROVIDER', NOW(), NOW())",
                UUID.randomUUID(), tenantId2, connectorId2
            );
        }, "Invalid provider should violate CHECK constraint");
    }

    @Test
    void testRunStatusConstraint() {
        // Valid status should work
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);
        
        ConnectorRun validRun = new ConnectorRun();
        validRun.setTenantId(tenantId);
        validRun.setConnectorId(connectorId);
        validRun.setJobType(ConnectorRun.JobType.DELETE);
        validRun.setStatus(ConnectorRun.RunStatus.PENDING);
        validRun.setCorrelationId("test-correlation-id");
        ConnectorRun saved = runRepository.save(validRun);
        assertNotNull(saved.getId());

        // Invalid status should fail (test via direct SQL)
        UUID tenantId2 = UUID.randomUUID();
        UUID connectorId2 = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO connector.connector_runs (id, tenant_id, connector_id, job_type, status, correlation_id, attempts, max_attempts, created_at, updated_at) VALUES (?, ?, ?, 'DELETE', 'INVALID_STATUS', 'test', 0, 5, NOW(), NOW())",
                UUID.randomUUID(), tenantId2, connectorId2
            );
        }, "Invalid status should violate CHECK constraint");
    }

    @Test
    void testCredentialUniqueConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // First credential should succeed
        ConnectorCredential cred1 = new ConnectorCredential();
        cred1.setTenantId(tenantId);
        cred1.setConnectorId(connectorId);
        cred1.setProvider(ConnectorCredential.CredentialProvider.ENV);
        credentialRepository.save(cred1);

        // Second credential with same tenant_id + connector_id should fail
        ConnectorCredential cred2 = new ConnectorCredential();
        cred2.setTenantId(tenantId);
        cred2.setConnectorId(connectorId);
        cred2.setProvider(ConnectorCredential.CredentialProvider.AWS_SECRETS_MANAGER);

        assertThrows(DataIntegrityViolationException.class, () -> {
            credentialRepository.saveAndFlush(cred2);
        }, "Duplicate tenant_id + connector_id should violate unique constraint");
    }

    @Test
    void testFindByTenantIdAndConnectorId() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        ConnectorCredential cred = new ConnectorCredential();
        cred.setTenantId(tenantId);
        cred.setConnectorId(connectorId);
        cred.setProvider(ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
        cred.setSecretId("test-secret");
        credentialRepository.save(cred);

        var found = credentialRepository.findByTenantIdAndConnectorId(tenantId, connectorId);
        assertTrue(found.isPresent(), "Should find credential by tenant and connector ID");
        assertEquals("test-secret", found.get().getSecretId());
    }

    @Test
    void testFindRetryableRuns() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // Create a FAILED_RETRYABLE run with next_retry_at in the past
        ConnectorRun retryableRun = new ConnectorRun();
        retryableRun.setTenantId(tenantId);
        retryableRun.setConnectorId(connectorId);
        retryableRun.setJobType(ConnectorRun.JobType.AUDIT_PULL);
        retryableRun.setStatus(ConnectorRun.RunStatus.FAILED_RETRYABLE);
        retryableRun.setCorrelationId("retryable-correlation");
        retryableRun.setNextRetryAt(Instant.now().minusSeconds(60));
        retryableRun.setAttempts(2);
        runRepository.save(retryableRun);

        // Create a FAILED_RETRYABLE run with next_retry_at in the future
        ConnectorRun futureRetry = new ConnectorRun();
        futureRetry.setTenantId(tenantId);
        futureRetry.setConnectorId(connectorId);
        futureRetry.setJobType(ConnectorRun.JobType.EXPORT);
        futureRetry.setStatus(ConnectorRun.RunStatus.FAILED_RETRYABLE);
        futureRetry.setCorrelationId("future-correlation");
        futureRetry.setNextRetryAt(Instant.now().plusSeconds(300));
        runRepository.save(futureRetry);

        // Create a SUCCEEDED run (should not be returned)
        ConnectorRun succeededRun = new ConnectorRun();
        succeededRun.setTenantId(tenantId);
        succeededRun.setConnectorId(connectorId);
        succeededRun.setJobType(ConnectorRun.JobType.DELETE);
        succeededRun.setStatus(ConnectorRun.RunStatus.SUCCEEDED);
        succeededRun.setCorrelationId("succeeded-correlation");
        runRepository.save(succeededRun);

        List<ConnectorRun> retryable = runRepository.findRetryableRuns(Instant.now());

        assertEquals(1, retryable.size(), "Should find only one retryable run (past retry time)");
        assertEquals("retryable-correlation", retryable.get(0).getCorrelationId());
    }

    @Test
    void testFindRecentRunsByConnector() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // Create multiple runs
        for (int i = 0; i < 3; i++) {
            ConnectorRun run = new ConnectorRun();
            run.setTenantId(tenantId);
            run.setConnectorId(connectorId);
            run.setJobType(ConnectorRun.JobType.AUDIT_PULL);
            run.setStatus(ConnectorRun.RunStatus.SUCCEEDED);
            run.setCorrelationId("correlation-" + i);
            runRepository.save(run);
            try {
                Thread.sleep(10); // Ensure different created_at timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<ConnectorRun> recent = runRepository.findByTenantIdAndConnectorIdOrderByCreatedAtDesc(tenantId, connectorId);

        assertEquals(3, recent.size(), "Should find all runs for connector");
        assertEquals("correlation-2", recent.get(0).getCorrelationId(), "Most recent should be first");
        assertEquals("correlation-0", recent.get(2).getCorrelationId(), "Oldest should be last");
    }
}
