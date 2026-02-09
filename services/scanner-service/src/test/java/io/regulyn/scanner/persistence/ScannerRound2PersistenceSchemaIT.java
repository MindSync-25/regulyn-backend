package io.regulyn.scanner.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
public class ScannerRound2PersistenceSchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("scanner_round2_test")
        .withUsername("test")
        .withPassword("test");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setup() {
        DataSource dataSource = new DriverManagerDataSource(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute(
            "TRUNCATE " +
                "scanner.remediation_task_events, " +
                "scanner.remediation_tasks, " +
                "scanner.scanned_pages, " +
                "scanner.scan_run_evidence_refs, " +
                "scanner.scan_findings, " +
                "scanner.scan_runs, " +
                "scanner.scan_sources " +
            "RESTART IDENTITY CASCADE"
        );
    }

    @Test
    void flywayMigratesSuccessfully_andNewTablesExist() {
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'scanner'",
            String.class
        );

        Set<String> tables = tableNames.stream().collect(Collectors.toSet());

        assertThat(tables).contains(
            "scanned_pages",
            "remediation_tasks",
            "remediation_task_events",
            "scan_run_evidence_refs"
        );
    }

    @Test
    void remediationTasks_partialUniqueIndex_preventsDuplicateActiveTasks() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String fingerprint = "f".repeat(64);

        insertRemediationTask(tenantId, sourceId, runId, fingerprint, "OPEN");

        assertThatThrownBy(() -> insertRemediationTask(tenantId, sourceId, runId, fingerprint, "OPEN"))
            .isInstanceOf(DataIntegrityViolationException.class);

        insertRemediationTask(tenantId, sourceId, runId, fingerprint, "CLOSED");
    }

    @Test
    void scanFindings_uniquePerRun_whenFingerprintPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        insertSourceAndRun(tenantId, sourceId, runId);

        String fingerprint = "a".repeat(64);
        insertScanFinding(tenantId, runId, fingerprint);

        assertThatThrownBy(() -> insertScanFinding(tenantId, runId, fingerprint))
            .isInstanceOf(DataIntegrityViolationException.class);

        insertScanFinding(tenantId, runId, null);
        insertScanFinding(tenantId, runId, null);
    }

    @Test
    void scannedPages_uniqueUrlPerRun() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        insertSourceAndRun(tenantId, sourceId, runId);

        String urlHash = "b".repeat(64);
        insertScannedPage(tenantId, sourceId, runId, "https://example.com", urlHash);

        assertThatThrownBy(() -> insertScannedPage(tenantId, sourceId, runId, "https://example.com", urlHash))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertSourceAndRun(UUID tenantId, UUID sourceId, UUID runId) {
        jdbcTemplate.update(
            "INSERT INTO scanner.scan_sources " +
                "(source_id, tenant_id, source_name, system_id, source_type, status, base_url, auth_type, auth_ref, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb)",
            sourceId,
            tenantId,
            "Test Source",
            UUID.randomUUID(),
            "MOCK",
            "ACTIVE",
            null,
            "NONE",
            null
        );

        jdbcTemplate.update(
            "INSERT INTO scanner.scan_runs (run_id, tenant_id, source_id, scan_mode, status) VALUES (?, ?, ?, ?, ?)",
            runId,
            tenantId,
            sourceId,
            "BOTH",
            "QUEUED"
        );
    }

    private void insertScanFinding(UUID tenantId, UUID runId, String fingerprint) {
        jdbcTemplate.update(
            "INSERT INTO scanner.scan_findings " +
                "(tenant_id, run_id, finding_type, entity_type, risk_level, confidence, details, finding_fingerprint) " +
                "VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?)",
            tenantId,
            runId,
            "ENTITY",
            "customer_profile",
            "MED",
            80,
            fingerprint
        );
    }

    private void insertRemediationTask(UUID tenantId, UUID sourceId, UUID runId, String fingerprint, String status) {
        jdbcTemplate.update(
            "INSERT INTO scanner.remediation_tasks " +
                "(tenant_id, source_id, run_id, finding_fingerprint, title, severity, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            tenantId,
            sourceId,
            runId,
            fingerprint,
            "Remediate issue",
            "HIGH",
            status
        );
    }

    private void insertScannedPage(UUID tenantId, UUID sourceId, UUID runId, String url, String urlHash) {
        jdbcTemplate.update(
            "INSERT INTO scanner.scanned_pages " +
                "(tenant_id, source_id, run_id, url, url_hash) " +
                "VALUES (?, ?, ?, ?, ?)",
            tenantId,
            sourceId,
            runId,
            url,
            urlHash
        );
    }
}