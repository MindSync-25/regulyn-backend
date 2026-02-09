package com.regulyn.vendor.persistence;

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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
public class VendorAccessTelemetrySchemaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_access_schema_test")
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
                "vendor.vendor_access_events, " +
                "vendor.vendor_access_exports " +
            "RESTART IDENTITY CASCADE"
        );
    }

    @Test
    void flywayMigratesSuccessfully_andNewTablesExist() {
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'vendor'",
            String.class
        );

        Set<String> tables = tableNames.stream().collect(Collectors.toSet());

        assertThat(tables).contains(
            "vendor_access_events",
            "vendor_access_exports"
        );
    }

    @Test
    void idempotencyConstraints_preventDuplicateEventsAndExports() {
        UUID tenantId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        OffsetDateTime accessedAt = OffsetDateTime.now().minusHours(1);
        String correlationId = "corr-123";

        insertVendorAccessEvent(UUID.randomUUID(), tenantId, vendorId, accessedAt, correlationId, "READ");

        assertThatThrownBy(() ->
            insertVendorAccessEvent(UUID.randomUUID(), tenantId, vendorId, accessedAt, correlationId, "READ")
        ).isInstanceOf(DataIntegrityViolationException.class);

        OffsetDateTime rangeStart = OffsetDateTime.now().minusDays(1);
        OffsetDateTime rangeEnd = OffsetDateTime.now();
        String idempotencyKey = "export-key";

        insertVendorAccessExport(UUID.randomUUID(), tenantId, vendorId, rangeStart, rangeEnd, idempotencyKey);

        assertThatThrownBy(() ->
            insertVendorAccessExport(UUID.randomUUID(), tenantId, vendorId, rangeStart, rangeEnd, idempotencyKey)
        ).isInstanceOf(DataIntegrityViolationException.class);

        insertVendorAccessExport(UUID.randomUUID(), tenantId, vendorId, rangeStart, rangeEnd, null);
        insertVendorAccessExport(UUID.randomUUID(), tenantId, vendorId, rangeStart, rangeEnd, null);
    }

    @Test
    void nullableFields_acceptNullValues() {
        UUID tenantId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        OffsetDateTime accessedAt = OffsetDateTime.now().minusMinutes(30);

        jdbcTemplate.update(
            "INSERT INTO vendor.vendor_access_events " +
                "(access_event_id, tenant_id, vendor_id, system_name, source, access_type, subject_ref, data_categories, " +
                "purpose_ref, purpose_version, accessed_at, correlation_id, actor_type, actor_id, ip, user_agent, " +
                "result, raw_payload_hash, raw_payload_ref) " +
                "VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, NULL, ?, ?, ?, NULL, NULL, NULL, ?, ?, NULL)",
            UUID.randomUUID(),
            tenantId,
            vendorId,
            "CRM",
            "READ",
            accessedAt,
            "corr-nullables",
            "USER",
            "ALLOWED",
            "hash-null"
        );

        jdbcTemplate.update(
            "INSERT INTO vendor.vendor_access_exports " +
                "(export_id, tenant_id, vendor_id, requested_by_user_id, range_start, range_end, format, status, " +
                "idempotency_key, artifact_ref, file_hash, notes) " +
                "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, NULL, NULL, NULL, NULL)",
            UUID.randomUUID(),
            tenantId,
            vendorId,
            OffsetDateTime.now().minusDays(2),
            OffsetDateTime.now().minusDays(1),
            "CSV",
            "REQUESTED"
        );
    }

    private void insertVendorAccessEvent(
        UUID eventId,
        UUID tenantId,
        UUID vendorId,
        OffsetDateTime accessedAt,
        String correlationId,
        String accessType
    ) {
        jdbcTemplate.update(
            "INSERT INTO vendor.vendor_access_events " +
                "(access_event_id, tenant_id, vendor_id, system_name, access_type, accessed_at, correlation_id, actor_type, result, raw_payload_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            eventId,
            tenantId,
            vendorId,
            "CRM",
            accessType,
            accessedAt,
            correlationId,
            "SYSTEM",
            "ALLOWED",
            "hash-" + eventId
        );
    }

    private void insertVendorAccessExport(
        UUID exportId,
        UUID tenantId,
        UUID vendorId,
        OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd,
        String idempotencyKey
    ) {
        jdbcTemplate.update(
            "INSERT INTO vendor.vendor_access_exports " +
                "(export_id, tenant_id, vendor_id, range_start, range_end, format, status, idempotency_key) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            exportId,
            tenantId,
            vendorId,
            rangeStart,
            rangeEnd,
            "CSV",
            "REQUESTED",
            idempotencyKey
        );
    }
}
