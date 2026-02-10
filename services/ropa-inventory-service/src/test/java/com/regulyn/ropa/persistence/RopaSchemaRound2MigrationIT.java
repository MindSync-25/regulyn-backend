package com.regulyn.ropa.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RopaSchemaRound2MigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_round2_schema_test")
        .withUsername("test")
        .withPassword("test");

    @Test
    void shouldApplyRound2MigrationAndSchema() throws Exception {
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("ropa")
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection conn = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            List<String> tables = List.of(
                "retention_policies_system",
                "retention_policies_activity",
                "retention_policies_category_purpose",
                "cross_border_transfers",
                "cross_border_transfer_data_categories",
                "cross_border_transfer_purpose_versions",
                "ropa_report_exports"
            );
            for (String table : tables) {
                assertThat(tableExists(conn, table)).isTrue();
            }

            assertThat(constraintExists(conn, "uq_rps_tenant_system")).isTrue();
            assertThat(constraintExists(conn, "uq_rpa_tenant_activity")).isTrue();
            assertThat(constraintExists(conn, "uq_rpcp_tenant_cat_purpose")).isTrue();
            assertThat(constraintExists(conn, "uq_cbt_tenant_activity_vendor_regions_mech")).isTrue();

            assertThat(constraintExists(conn, "chk_rps_retention_basis")).isTrue();
            assertThat(constraintExists(conn, "chk_rpa_retention_basis")).isTrue();
            assertThat(constraintExists(conn, "chk_rpcp_retention_basis")).isTrue();
            assertThat(constraintExists(conn, "chk_cbt_transfer_mechanism")).isTrue();
            assertThat(constraintExists(conn, "chk_cbt_frequency")).isTrue();
            assertThat(constraintExists(conn, "chk_rre_report_type")).isTrue();
            assertThat(constraintExists(conn, "chk_rre_status")).isTrue();

            assertThat(indexExists(conn, "idx_cbt_tenant_vendor")).isTrue();
            assertThat(indexExists(conn, "idx_cbt_tenant_activity")).isTrue();
            assertThat(indexExists(conn, "idx_cbt_tenant_system")).isTrue();
            assertThat(indexExists(conn, "idx_cbt_tenant_src")).isTrue();
            assertThat(indexExists(conn, "idx_cbt_tenant_dst")).isTrue();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = 'ropa' AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean constraintExists(Connection conn, String constraintName) throws Exception {
        String sql = "SELECT 1 FROM pg_constraint c "
            + "JOIN pg_namespace n ON n.oid = c.connamespace "
            + "WHERE n.nspname = 'ropa' AND c.conname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(Connection conn, String indexName) throws Exception {
        String sql = "SELECT 1 FROM pg_indexes WHERE schemaname = 'ropa' AND indexname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
