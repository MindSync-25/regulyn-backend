package io.regulyn.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IdentitySchemaFlywayIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("identity")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("identity")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void tenants_round2_columns_exist() throws Exception {
        assertThat(columnExists("tenants", "activated_at")).isTrue();
        assertThat(columnExists("tenants", "suspended_at")).isTrue();
        assertThat(columnExists("tenants", "deleted_at")).isTrue();
        assertThat(columnExists("tenants", "admin_bootstrapped_at")).isTrue();
        assertThat(columnExists("tenants", "admin_bootstrap_user_id")).isTrue();
        assertThat(columnExists("tenants", "compliance_hold")).isTrue();
        assertThat(columnExists("tenants", "compliance_hold_reason")).isTrue();
        assertThat(columnExists("tenants", "delete_requested_at")).isTrue();
        assertThat(columnExists("tenants", "read_only")).isTrue();
        assertThat(columnExists("tenants", "read_only_reason")).isTrue();
        assertThat(columnExists("tenants", "read_only_since")).isTrue();
        assertThat(columnExists("tenants", "plan_code")).isTrue();
    }

    @Test
    void tenants_status_check_allows_round2() throws Exception {
        UUID tenantId = createTenant("Status Test");

        String status = queryString("select status from identity.tenants where tenant_id = ?", tenantId);
        assertThat(status).isEqualTo("DRAFT");

        executeUpdate("update identity.tenants set status = 'SUSPENDED' where tenant_id = ?", tenantId);

        assertThrows(SQLException.class, () ->
                executeUpdate("update identity.tenants set status = 'INVALID' where tenant_id = ?", tenantId)
        );
    }

    @Test
    void tenants_status_constraint_round2_only() throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select conname, pg_get_constraintdef(oid) as def " +
                     "from pg_constraint where conrelid = 'identity.tenants'::regclass and contype = 'c'")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean foundStatus = false;
                int statusConstraints = 0;
                while (rs.next()) {
                    String name = rs.getString("conname");
                    String def = rs.getString("def");
                    if (def != null && def.toLowerCase().contains("status")) {
                        statusConstraints++;
                        foundStatus = true;
                        assertThat(name).isEqualTo("chk_tenants_status_round2");
                        assertThat(def).contains("DRAFT", "ACTIVE", "SUSPENDED", "DELETED");
                    }
                }
                assertTrue(foundStatus);
                assertThat(statusConstraints).isEqualTo(1);
            }
        }
    }

    @Test
    void tenant_feature_flags_unique() throws Exception {
        UUID tenantId = createTenant("Feature Flags");

        executeUpdate(
                "insert into identity.tenant_feature_flags (feature_flag_id, tenant_id, flag_key, enabled) values (?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, "FEATURE_A", true
        );

        assertThrows(SQLException.class, () ->
                executeUpdate(
                        "insert into identity.tenant_feature_flags (feature_flag_id, tenant_id, flag_key, enabled) values (?, ?, ?, ?)",
                        UUID.randomUUID(), tenantId, "FEATURE_A", false
                )
        );
    }

    @Test
    void tenant_plan_limits_defaults() throws Exception {
        UUID tenantId = createTenant("Plan Limits");

        executeUpdate("insert into identity.tenant_plan_limits (tenant_id) values (?)", tenantId);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select max_users, dsar_per_month, exports_per_month from identity.tenant_plan_limits where tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("max_users")).isEqualTo(5);
                assertThat(rs.getInt("dsar_per_month")).isEqualTo(50);
                assertThat(rs.getInt("exports_per_month")).isEqualTo(50);
            }
        }
    }

    @Test
    void tenant_monthly_usage_unique() throws Exception {
        UUID tenantId = createTenant("Monthly Usage");

        executeUpdate(
                "insert into identity.tenant_monthly_usage (usage_id, tenant_id, year_month) values (?, ?, ?)",
                UUID.randomUUID(), tenantId, 202602
        );

        assertThrows(SQLException.class, () ->
                executeUpdate(
                        "insert into identity.tenant_monthly_usage (usage_id, tenant_id, year_month) values (?, ?, ?)",
                        UUID.randomUUID(), tenantId, 202602
                )
        );
    }

    @Test
    void tenant_monthly_usage_defaults() throws Exception {
        UUID tenantId = createTenant("Monthly Usage Defaults");

        executeUpdate(
                "insert into identity.tenant_monthly_usage (usage_id, tenant_id, year_month) values (?, ?, ?)",
                UUID.randomUUID(), tenantId, 202603
        );

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select dsar_count, export_count from identity.tenant_monthly_usage where tenant_id = ? and year_month = ?")) {
            ps.setObject(1, tenantId);
            ps.setInt(2, 202603);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("dsar_count")).isEqualTo(0);
                assertThat(rs.getInt("export_count")).isEqualTo(0);
            }
        }
    }

    @Test
    void user_invites_partial_unique() throws Exception {
        UUID tenantId = createTenant("Invites");

        executeUpdate(
                "insert into identity.user_invites (invite_id, tenant_id, email, roles_json, token_hash, expires_at) values (?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID(), tenantId, "user@example.com", "[\"TENANT_ADMIN\"]", "hash-1", Timestamp.from(Instant.now().plusSeconds(3600))
        );

        assertThrows(SQLException.class, () ->
                executeUpdate(
                        "insert into identity.user_invites (invite_id, tenant_id, email, roles_json, token_hash, expires_at) values (?, ?, ?, ?::jsonb, ?, ?)",
                        UUID.randomUUID(), tenantId, "user@example.com", "[\"TENANT_ADMIN\"]", "hash-2", Timestamp.from(Instant.now().plusSeconds(3600))
                )
        );

        executeUpdate(
                "update identity.user_invites set used_at = now() where tenant_id = ? and lower(email) = lower(?) and used_at is null",
                tenantId, "user@example.com"
        );

        executeUpdate(
                "insert into identity.user_invites (invite_id, tenant_id, email, roles_json, token_hash, expires_at) values (?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID(), tenantId, "user@example.com", "[\"TENANT_ADMIN\"]", "hash-3", Timestamp.from(Instant.now().plusSeconds(3600))
        );
    }

    @Test
    void api_keys_extended_columns_exist() throws Exception {
        assertThat(columnExists("api_keys", "key_version")).isTrue();
        assertThat(columnExists("api_keys", "revoked_at")).isTrue();
        assertThat(columnExists("api_keys", "rotated_from_api_key_id")).isTrue();
        assertThat(columnExists("api_keys", "prefix")).isTrue();
        assertThat(columnExists("api_keys", "hash_alg")).isTrue();
        assertThat(columnExists("api_keys", "last_used_at")).isTrue();
        assertThat(columnExists("api_keys", "key_hash")).isFalse();
    }

    @Test
    void audit_outbox_smoke() throws Exception {
        assertThat(tableExists("audit_events")).isTrue();
        assertThat(tableExists("outbox_events")).isTrue();
    }

    @Test
    void foreign_keys_point_to_users_user_id() throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select conname, pg_get_constraintdef(oid) as def " +
                     "from pg_constraint " +
                     "where conrelid in ('identity.tenants'::regclass, 'identity.user_invites'::regclass) " +
                     "and contype = 'f'")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean sawAdminBootstrap = false;
                boolean sawInviteUsedBy = false;
                boolean sawInviteCreatedBy = false;
                while (rs.next()) {
                    String def = rs.getString("def");
                    if (def != null && def.contains("admin_bootstrap_user_id")) {
                        sawAdminBootstrap = true;
                        assertThat(def).contains("REFERENCES identity.users(user_id)");
                    }
                    if (def != null && def.contains("used_by_user_id")) {
                        sawInviteUsedBy = true;
                        assertThat(def).contains("REFERENCES identity.users(user_id)");
                    }
                    if (def != null && def.contains("created_by_user_id")) {
                        sawInviteCreatedBy = true;
                        assertThat(def).contains("REFERENCES identity.users(user_id)");
                    }
                }
                assertTrue(sawAdminBootstrap);
                assertTrue(sawInviteUsedBy);
                assertTrue(sawInviteCreatedBy);
            }
        }
    }

    @Test
    void invite_partial_unique_index_exists() throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select indexdef from pg_indexes where schemaname = 'identity' and tablename = 'user_invites'")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    String def = rs.getString("indexdef");
                    if (def != null && def.contains("uq_user_invites_active_email")) {
                        found = true;
                        assertThat(def.toLowerCase()).contains("lower((email")
                                .contains("where (used_at is null)");
                    }
                }
                assertTrue(found);
            }
        }
    }

    @Test
    void tenants_default_flags() throws Exception {
        UUID tenantId = createTenant("Defaults");

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select status, read_only, compliance_hold from identity.tenants where tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("DRAFT");
                assertThat(rs.getBoolean("read_only")).isFalse();
                assertThat(rs.getBoolean("compliance_hold")).isFalse();
            }
        }
    }

    @Test
    void local_seed_sanity() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID adminId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID adminRoleId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        Long seedCount = queryLong("select count(*) from identity.tenants where tenant_id = ?", tenantId);
        if (seedCount == null || seedCount == 0L) {
            return;
        }

        String status = queryString("select status from identity.tenants where tenant_id = ?", tenantId);
        assertThat(status).isEqualTo("ACTIVE");

        String email = queryString("select email from identity.users where user_id = ?", adminId);
        assertThat(email).isEqualTo("admin@local.test");

        Long roleCount = queryLong(
                "select count(*) from identity.user_roles where user_id = ? and role_id = ? and tenant_id = ?",
                adminId, adminRoleId, tenantId
        );
        assertThat(roleCount).isEqualTo(1L);
    }

    private UUID createTenant(String name) throws Exception {
        UUID tenantId = UUID.randomUUID();
        executeUpdate("insert into identity.tenants (tenant_id, name) values (?, ?)", tenantId, name);
        return tenantId;
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select 1 from information_schema.columns where table_schema = 'identity' and table_name = ? and column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "select 1 from information_schema.tables where table_schema = 'identity' and table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String queryString(String sql, Object... params) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return null;
            }
        }
    }

    private Long queryLong(String sql, Object... params) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return null;
            }
        }
    }

    private void executeUpdate(String sql, Object... params) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
        }
    }

    private void bindParams(PreparedStatement ps, Object... params) throws Exception {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
