package com.regulyn.nominee;

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
class NomineeDocumentsSchemaIT {

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
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "nominee");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void nomineeDocumentsTableAndColumnsExist() {
        assertTableExists("nominee_documents");
        assertColumnExists("nominee_documents", "tenant_id");
        assertColumnExists("nominee_documents", "nominee_id");
        assertColumnExists("nominee_documents", "claim_id");
        assertColumnExists("nominee_documents", "verification_step");
        assertColumnExists("nominee_documents", "artifact_ref");
        assertColumnExists("nominee_documents", "sha256_hash");
        assertColumnExists("nominee_documents", "filename");
        assertColumnExists("nominee_documents", "content_type");
        assertColumnExists("nominee_documents", "size_bytes");
        assertColumnExists("nominee_documents", "uploaded_at");
        assertColumnExists("nominee_documents", "source_type");
        assertColumnExists("nominee_documents", "idempotency_key");
    }

    @Test
    void nomineeDocumentsConstraintsAndIndexesExist() {
        assertCheckConstraintContains("nominee_documents", "size_bytes >= 0");
        assertUniqueConstraintContains("nominee_documents", "UNIQUE (tenant_id, nominee_id, sha256_hash)");

        assertIndexContains("nominee_documents", "tenant_id, nominee_id, uploaded_at DESC");
        assertIndexContains("nominee_documents", "tenant_id, nominee_id, verification_step");
        assertIndexContains("nominee_documents", "tenant_id, sha256_hash");
        assertIndexContains("nominee_documents", "tenant_id, claim_id");
        assertIndexContains("nominee_documents", "claim_id IS NOT NULL");
    }

    @Test
    void nomineeVerificationRequirementsTableExists() {
        assertTableExists("nominee_verification_requirements");
        assertColumnExists("nominee_verification_requirements", "tenant_id");
        assertColumnExists("nominee_verification_requirements", "verification_step");
        assertPrimaryKeyContains("nominee_verification_requirements", "tenant_id, verification_step");
    }

    @Test
    void claimDocumentsBridgeColumnExists() {
        assertColumnExists("claim_documents", "nominee_document_id");
        assertIndexContains("claim_documents", "nominee_document_id");
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'nominee' AND table_name = ?",
                Integer.class,
                tableName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'nominee' AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertCheckConstraintContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'nominee' AND t.relname = ? AND c.contype = 'c'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertUniqueConstraintContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'nominee' AND t.relname = ? AND c.contype = 'u'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertPrimaryKeyContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'nominee' AND t.relname = ? AND c.contype = 'p'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertIndexContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'nominee' AND tablename = ?",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }
}
