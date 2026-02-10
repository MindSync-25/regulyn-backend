package com.regulyn.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class EmployeeRound2Part1SchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "employee");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schemaTablesExist() {
        assertTableExists("employee_resumes");
        assertTableExists("hr_document_rules");
        assertTableExists("hr_document_metadata");
        assertTableExists("employee_exit_workflows");
        assertTableExists("employee_exit_workflow_steps");
        assertTableExists("employee_access_logs");
        assertTableExists("employee_access_log_exports");
    }

    @Test
    void keyColumnsExist() {
        assertColumnExists("employee_resumes", "resume_id");
        assertColumnExists("employee_resumes", "tenant_id");
        assertColumnExists("employee_resumes", "delete_after");
        assertColumnExists("employee_resumes", "status");
        assertColumnExists("employee_resumes", "storage_ref");
        assertColumnExists("employee_resumes", "deletion_artifact_ref");

        assertColumnExists("hr_document_rules", "rule_id");
        assertColumnExists("hr_document_rules", "tenant_id");
        assertColumnExists("hr_document_rules", "doc_type");
        assertColumnExists("hr_document_rules", "sensitivity");
        assertColumnExists("hr_document_rules", "allowed_roles");
        assertColumnExists("hr_document_rules", "retention_days");
        assertColumnExists("hr_document_rules", "encryption_required");

        assertColumnExists("hr_document_metadata", "doc_id");
        assertColumnExists("hr_document_metadata", "tenant_id");
        assertColumnExists("hr_document_metadata", "employee_id");
        assertColumnExists("hr_document_metadata", "doc_type");
        assertColumnExists("hr_document_metadata", "delete_after");
        assertColumnExists("hr_document_metadata", "status");

        assertColumnExists("employee_exit_workflows", "workflow_id");
        assertColumnExists("employee_exit_workflows", "tenant_id");
        assertColumnExists("employee_exit_workflows", "employee_id");
        assertColumnExists("employee_exit_workflows", "terminated_at");
        assertColumnExists("employee_exit_workflows", "status");
        assertColumnExists("employee_exit_workflows", "evidence_bundle_ref");

        assertColumnExists("employee_exit_workflow_steps", "step_id");
        assertColumnExists("employee_exit_workflow_steps", "workflow_id");
        assertColumnExists("employee_exit_workflow_steps", "step_key");
        assertColumnExists("employee_exit_workflow_steps", "status");
        assertColumnExists("employee_exit_workflow_steps", "attempt_count");

        assertColumnExists("employee_access_logs", "access_log_id");
        assertColumnExists("employee_access_logs", "tenant_id");
        assertColumnExists("employee_access_logs", "user_id");
        assertColumnExists("employee_access_logs", "action");
        assertColumnExists("employee_access_logs", "result");
        assertColumnExists("employee_access_logs", "occurred_at");

        assertColumnExists("employee_access_log_exports", "access_export_id");
        assertColumnExists("employee_access_log_exports", "tenant_id");
        assertColumnExists("employee_access_log_exports", "export_scope");
        assertColumnExists("employee_access_log_exports", "status");
        assertColumnExists("employee_access_log_exports", "idempotency_key");
        assertColumnExists("employee_access_log_exports", "artifact_ref");
    }

    @Test
    void uniqueConstraintsExist() {
        assertUniqueIndexContains("employee_resumes", "tenant_id, idempotency_key", "idempotency_key IS NOT NULL");
        assertUniqueConstraintContains("hr_document_rules", "UNIQUE (tenant_id, doc_type)");
        assertUniqueIndexContains("hr_document_metadata", "tenant_id, employee_id, idempotency_key", "idempotency_key IS NOT NULL");
        assertUniqueConstraintContains("employee_exit_workflows", "UNIQUE (tenant_id, employee_id, terminated_at)");
        assertUniqueConstraintContains("employee_exit_workflow_steps", "UNIQUE (tenant_id, workflow_id, step_key)");
        assertUniqueConstraintContains("employee_access_log_exports", "UNIQUE (tenant_id, idempotency_key)");
    }

    @Test
    void checkConstraintsExist() {
        assertCheckConstraintContains("employee_access_logs", "employee_id IS NOT NULL");
        assertCheckConstraintContains("employee_access_logs", "doc_id IS NOT NULL");
        assertCheckConstraintContains("employee_resumes", "employee_id IS NOT NULL");
        assertCheckConstraintContains("employee_resumes", "employee_ref IS NOT NULL");
        assertCheckConstraintContains("hr_document_rules", "array_length(allowed_roles, 1) > 0");

        assertCheckConstraintContains("employee_access_log_exports", "export_scope = 'BY_EMPLOYEE'");
        assertCheckConstraintContains("employee_access_log_exports", "export_scope = 'BY_USER'");
        assertCheckConstraintContains("employee_access_log_exports", "export_scope = 'BY_DATE_RANGE'");
        assertCheckConstraintContains("employee_access_log_exports", "from_ts <= to_ts");
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'employee' AND table_name = ?",
                Integer.class,
                tableName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'employee' AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertUniqueConstraintContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'employee' AND t.relname = ? AND c.contype = 'u'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertUniqueIndexContains(String tableName, String... expectedSubstrings) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'employee' AND tablename = ?",
                String.class,
                tableName
        );
        assertThat(defs).isNotEmpty();
        assertThat(defs).anyMatch(def -> {
            boolean allMatch = def.contains("UNIQUE");
            for (String expectedSubstring : expectedSubstrings) {
                allMatch = allMatch && def.contains(expectedSubstring);
            }
            return allMatch;
        });
    }

    private void assertCheckConstraintContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'employee' AND t.relname = ? AND c.contype = 'c'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }
}
