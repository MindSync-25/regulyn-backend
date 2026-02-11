package com.regulyn.guardian.persistence;

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
class ChildrenGuardianRound2Part1SchemaIT {

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
        registry.add("spring.flyway.schemas", () -> "guardian,children");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void round2TablesExist() {
        assertTableExists("age_threshold_rules");
        assertTableExists("esign_requests");
        assertTableExists("esign_webhook_events");
        assertTableExists("child_majority_transitions");
        assertTableExists("children_evidence_exports");
    }

    @Test
    void round2ColumnsExist() {
        assertColumnExists("age_threshold_rules", "tenant_id");
        assertColumnExists("age_threshold_rules", "region_country_code");
        assertColumnExists("age_threshold_rules", "region_state_code");
        assertColumnExists("age_threshold_rules", "threshold_age_years");
        assertColumnExists("age_threshold_rules", "is_default");
        assertColumnExists("age_threshold_rules", "min_legal_age_years");
        assertColumnExists("age_threshold_rules", "max_legal_age_years");

        assertColumnExists("esign_requests", "tenant_id");
        assertColumnExists("esign_requests", "child_id");
        assertColumnExists("esign_requests", "guardian_id");
        assertColumnExists("esign_requests", "consent_id");
        assertColumnExists("esign_requests", "doc_type");
        assertColumnExists("esign_requests", "doc_version");
        assertColumnExists("esign_requests", "provider");
        assertColumnExists("esign_requests", "provider_envelope_id");
        assertColumnExists("esign_requests", "signing_url");
        assertColumnExists("esign_requests", "status");
        assertColumnExists("esign_requests", "idempotency_key");
        assertColumnExists("esign_requests", "request_payload_sha256");

        assertColumnExists("esign_webhook_events", "tenant_id");
        assertColumnExists("esign_webhook_events", "provider");
        assertColumnExists("esign_webhook_events", "provider_envelope_id");
        assertColumnExists("esign_webhook_events", "provider_event_id");
        assertColumnExists("esign_webhook_events", "raw_payload");
        assertColumnExists("esign_webhook_events", "payload_sha256");
        assertColumnExists("esign_webhook_events", "signature_verification_status");

        assertColumnExists("child_majority_transitions", "tenant_id");
        assertColumnExists("child_majority_transitions", "child_id");
        assertColumnExists("child_majority_transitions", "dob");
        assertColumnExists("child_majority_transitions", "threshold_age_years");
        assertColumnExists("child_majority_transitions", "majority_date");
        assertColumnExists("child_majority_transitions", "transition_status");

        assertColumnExists("children_evidence_exports", "tenant_id");
        assertColumnExists("children_evidence_exports", "child_id");
        assertColumnExists("children_evidence_exports", "export_scope");
        assertColumnExists("children_evidence_exports", "status");
        assertColumnExists("children_evidence_exports", "evidence_bundle_ref");
        assertColumnExists("children_evidence_exports", "evidence_bundle_sha256");
        assertColumnExists("children_evidence_exports", "idempotency_key");

        assertColumnExists("consent_signed_artifacts", "esign_request_id");
        assertColumnExists("consent_signed_artifacts", "provider");
        assertColumnExists("consent_signed_artifacts", "provider_envelope_id");
        assertColumnExists("consent_signed_artifacts", "signed_payload_sha256");
        assertColumnExists("consent_signed_artifacts", "artifact_mime");
        assertColumnExists("consent_signed_artifacts", "artifact_stored_at");
        assertColumnExists("consent_signed_artifacts", "signature_verified");
        assertColumnExists("consent_signed_artifacts", "signature_verified_at");
        assertColumnExists("consent_signed_artifacts", "signature_verification_error");

        assertColumnExists("guardian_consents", "esign_request_id");
        assertColumnExists("guardian_consents", "signed_artifact_id");
        assertColumnExists("guardian_consents", "majority_date");
        assertColumnExists("guardian_consents", "region_country_code");
        assertColumnExists("guardian_consents", "region_state_code");
        assertColumnExists("guardian_consents", "threshold_age_years");
    }

    @Test
    void round2ConstraintsExist() {
        assertCheckConstraintContains("age_threshold_rules", "threshold_age_years BETWEEN min_legal_age_years AND max_legal_age_years");

        assertForeignKeyContains("esign_requests", "REFERENCES children.guardian_consents(consent_id)");
        assertForeignKeyContains("consent_signed_artifacts", "REFERENCES children.esign_requests(id)");
        assertForeignKeyContains("guardian_consents", "REFERENCES children.esign_requests(id)");
        assertForeignKeyContains("guardian_consents", "REFERENCES children.consent_signed_artifacts(artifact_id)");
    }

    @Test
    void round2IndexesExist() {
        assertIndexExists("idx_age_threshold_rules_region");
        assertIndexExists("idx_age_threshold_rules_default");
        assertIndexExists("idx_age_threshold_rules_tenant");
        assertIndexExists("idx_age_threshold_rules_region_lookup");

        assertIndexExists("idx_esign_requests_idempotency");
        assertIndexExists("idx_esign_requests_provider_envelope");
        assertIndexExists("idx_esign_requests_idempotent_form");
        assertIndexExists("idx_esign_requests_tenant_child");
        assertIndexExists("idx_esign_requests_tenant_guardian");
        assertIndexExists("idx_esign_requests_tenant_status");

        assertIndexExists("idx_esign_webhook_events_idempotency");
        assertIndexExists("idx_esign_webhook_events_envelope");
        assertIndexExists("idx_esign_webhook_events_received");

        assertIndexExists("idx_consent_signed_artifacts_tenant_esign");
        assertIndexExists("idx_guardian_consents_tenant_child");
        assertIndexExists("idx_guardian_consents_tenant_guardian");
        assertIndexExists("idx_guardian_consents_tenant_esign");

        assertIndexExists("idx_child_majority_transitions_unique");
        assertIndexExists("idx_child_majority_transitions_status");

        assertIndexExists("idx_children_evidence_exports_idempotency");
        assertIndexExists("idx_children_evidence_exports_child");
        assertIndexExists("idx_children_evidence_exports_status");
    }

    @Test
    void sha256ColumnsAreFixedLength() {
        assertColumnLength("esign_requests", "request_payload_sha256", 64);
        assertColumnLength("esign_webhook_events", "payload_sha256", 64);
        assertColumnLength("consent_signed_artifacts", "signed_payload_sha256", 64);
        assertColumnLength("children_evidence_exports", "evidence_bundle_sha256", 64);
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'children' AND table_name = ?",
                Integer.class,
                tableName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'children' AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertColumnLength(String tableName, String columnName, int length) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'children' AND table_name = ? AND column_name = ? AND character_maximum_length = ?",
                Integer.class,
                tableName,
                columnName,
                length
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }

    private void assertCheckConstraintContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'children' AND t.relname = ? AND c.contype = 'c'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertForeignKeyContains(String tableName, String expectedSubstring) {
        List<String> defs = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                        "JOIN pg_class t ON t.oid = c.conrelid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'children' AND t.relname = ? AND c.contype = 'f'",
                String.class,
                tableName
        );
        assertThat(defs).anyMatch(def -> def.contains(expectedSubstring));
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'children' AND indexname = ?",
                Integer.class,
                indexName
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);
    }
}
