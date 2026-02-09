package com.regulyn.dsar.schema;

import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.repository.DsarRequestRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DsarRound2Part1SchemaIT {

    private static final Logger logger = LoggerFactory.getLogger(DsarRound2Part1SchemaIT.class);
    private static final String MIGRATION_DIR = "classpath:db/migration";
    private static final String NEW_MIGRATION = "V6__dsar_round2_part1_attachments_escalations_bundlerefs.sql";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DsarRequestRepository dsarRequestRepository;

    @Test
    void verifyRound2Part1Schema() throws Exception {
        logMigrationInventory();

        assertTableExists("dsar", "dsar_attachments");
        assertTableExists("dsar", "dsar_escalation_tasks");
        assertTableExists("dsar", "dsar_evidence_bundle_refs");
        assertTableExists("dsar", "audit_events");
        assertTableExists("dsar", "outbox_events");

        assertIndexExists("dsar", "ux_dsar_attachments_tenant_dsar_version");
        assertIndexExists("dsar", "ux_dsar_attachments_tenant_dsar_idempotency");
        assertIndexExists("dsar", "ix_dsar_attachments_tenant_dsar_created");
        assertIndexExists("dsar", "ux_dsar_escalation_tasks_tenant_dsar_threshold");
        assertIndexExists("dsar", "ix_dsar_escalation_tasks_tenant_status_due");
        assertIndexExists("dsar", "ix_dsar_escalation_tasks_tenant_dsar");
        assertIndexExists("dsar", "ux_dsar_bundle_refs_tenant_dsar_close_event");
        assertIndexExists("dsar", "ix_dsar_bundle_refs_tenant_dsar_created");

        assertConstraintExists("dsar", "dsar_attachments", "ck_dsar_attachments_type");
        assertConstraintExists("dsar", "dsar_attachments", "ck_dsar_attachments_reference_value");
        assertConstraintExists("dsar", "dsar_escalation_tasks", "ck_dsar_escalation_threshold");
        assertConstraintExists("dsar", "dsar_escalation_tasks", "ck_dsar_escalation_status");
        assertConstraintExists("dsar", "dsar_escalation_tasks", "ck_dsar_escalation_threshold_days");
        assertConstraintExists("dsar", "dsar_evidence_bundle_refs", "ck_dsar_bundle_refs_status");

        insertMinimalDsarRequestAndReferences();
    }

    private void logMigrationInventory() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(MIGRATION_DIR + "/V*.sql");
        Optional<Resource> newMigration = Arrays.stream(resources)
            .filter(resource -> NEW_MIGRATION.equals(resource.getFilename()))
            .findFirst();

        int maxVersion = Arrays.stream(resources)
            .map(Resource::getFilename)
            .filter(name -> name != null && name.startsWith("V") && name.contains("__"))
            .map(name -> name.substring(1, name.indexOf("__")))
            .map(Integer::parseInt)
            .max(Comparator.naturalOrder())
            .orElse(0);

        logger.info("Migration dir: {}", MIGRATION_DIR);
        logger.info("Max migration version: {}", maxVersion);
        logger.info("New migration file: {}", NEW_MIGRATION);

        assertTrue(newMigration.isPresent(), "Expected new migration file to exist");
        assertEquals(6, maxVersion, "Expected max migration version to be 6");
    }

    private void insertMinimalDsarRequestAndReferences() {
        UUID tenantId = UUID.randomUUID();
        UUID dsarId = createDsarRequest(tenantId);
        UUID closeEventId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO dsar.dsar_attachments (id, tenant_id, dsar_id, attachment_type) " +
                "VALUES (?,?,?,?)",
            UUID.randomUUID(), tenantId, dsarId, "UPLOAD");

        jdbcTemplate.update(
            "INSERT INTO dsar.dsar_escalation_tasks (id, tenant_id, dsar_id, threshold, threshold_days, due_at, status) " +
                "VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID(), tenantId, dsarId, "D60", 60, Timestamp.from(Instant.now()), "CREATED");

        jdbcTemplate.update(
            "INSERT INTO dsar.dsar_evidence_bundle_refs (id, tenant_id, dsar_id, close_event_id, status) " +
                "VALUES (?,?,?,?,?)",
            UUID.randomUUID(), tenantId, dsarId, closeEventId, "CREATED");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsar.dsar_evidence_bundle_refs WHERE tenant_id = ? AND dsar_id = ?",
            Integer.class, tenantId, dsarId);
        assertNotNull(count);
        assertEquals(1, count);
    }

    private UUID createDsarRequest(UUID tenantId) {
        DsarRequestEntity entity = new DsarRequestEntity();
        entity.setTenantId(tenantId);
        entity.setDataPrincipalId(UUID.randomUUID());
        entity.setRequestId("req-" + UUID.randomUUID());
        entity.setRequestType("ACCESS");
        entity.setStatus("RECEIVED");
        entity.setRequesterEmail("test@example.com");

        DsarRequestEntity saved = dsarRequestRepository.saveAndFlush(entity);
        return saved.getRequestIdPk();
    }

    private void assertTableExists(String schema, String table) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
            Integer.class, schema, table);
        assertNotNull(count);
        assertTrue(count > 0, "Expected table to exist: " + schema + "." + table);
    }

    private void assertIndexExists(String schema, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            Integer.class, schema, indexName);
        assertNotNull(count);
        assertTrue(count > 0, "Expected index to exist: " + indexName);
    }

    private void assertConstraintExists(String schema, String table, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_schema = ? AND table_name = ? AND constraint_name = ?",
            Integer.class, schema, table, constraintName);
        assertNotNull(count);
        assertTrue(count > 0, "Expected constraint to exist: " + constraintName);
    }
}