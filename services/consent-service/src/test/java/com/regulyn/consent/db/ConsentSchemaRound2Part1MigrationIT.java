package com.regulyn.consent.db;

import com.regulyn.consent.ConsentServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ConsentServiceApplication.class)
@Testcontainers
class ConsentSchemaRound2Part1MigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("consent")
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

    @Test
    void schemaShouldContainRound2Part1TablesAndColumns() {
        assertThat(tableExists("purpose_versions")).isTrue();
        assertThat(tableExists("purpose_version_history")).isTrue();
        assertThat(tableExists("communication_consent_ledger")).isTrue();
        assertThat(tableExists("consent_invalidations")).isTrue();
        assertThat(tableExists("reconsent_requirements")).isTrue();

        assertThat(columnExists("consent_receipts", "purpose_version_id")).isTrue();
        assertThat(columnExists("consent_receipts", "language_code")).isTrue();
        assertThat(columnExists("consent_receipts", "notice_language_text_id")).isTrue();
        assertThat(columnExists("consent_receipts", "notice_content_hash_sha256")).isTrue();
        assertThat(columnExists("consent_receipts", "evidence_artifact_id")).isTrue();
    }

    @Test
    void schemaShouldContainRound2Part1ConstraintsAndIndexes() {
        assertThat(constraintExists("uq_comm_consent_idempotency")).isTrue();
        assertThat(constraintExists("uq_consent_invalidations")).isTrue();
        assertThat(constraintExists("uq_reconsent_requirements")).isTrue();

        assertThat(indexExists("ux_consent_receipts_round2_idempotency")).isTrue();

        assertThat(constraintExists("fk_purpose_versions_notice")).isTrue();
        assertThat(constraintExists("fk_purpose_versions_notice_version")).isTrue();
        assertThat(constraintExists("fk_consent_receipts_purpose_version")).isTrue();
        assertThat(constraintExists("fk_consent_receipts_notice_language")).isTrue();
        assertThat(constraintExists("fk_comm_consent_notice_language")).isTrue();
        assertThat(constraintExists("fk_consent_invalidations_receipt")).isTrue();
        assertThat(constraintExists("fk_consent_invalidations_purpose_version")).isTrue();
        assertThat(constraintExists("fk_reconsent_notice")).isTrue();
        assertThat(constraintExists("fk_reconsent_required_purpose_version")).isTrue();
        assertThat(constraintExists("fk_reconsent_satisfied_receipt")).isTrue();
    }

    @Test
    void partialUniqueIndexShouldBePresent() {
        String indexDef = jdbcTemplate.queryForObject(
            "select indexdef from pg_indexes where schemaname = 'consent' and indexname = 'ux_consent_receipts_round2_idempotency'",
            String.class
        );
        assertThat(indexDef).contains("WHERE").contains("purpose_version_id").contains("language_code");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'consent' and table_name = ?",
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_schema = 'consent' and table_name = ? and column_name = ?",
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                + "where n.nspname = 'consent' and c.conname = ?",
            Integer.class,
            constraintName
        );
        return count != null && count > 0;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from pg_indexes where schemaname = 'consent' and indexname = ?",
            Integer.class,
            indexName
        );
        return count != null && count > 0;
    }
}
