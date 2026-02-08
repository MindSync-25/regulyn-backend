package com.regulyn.incident.persistence;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class NoticePersistenceSchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("incident")
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

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void tablesExistInIncidentSchema() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'incident'",
                String.class
        );

        assertThat(tables).contains(
                "notice_templates",
                "notice_template_versions",
                "notice_drafts",
                "notice_approvals",
                "notice_dispatch_logs",
                "incident_escalations"
        );
    }

    @Test
    void templateVersionIsImmutable() {
        UUID templateId = insertNoticeTemplate();
        UUID versionId = insertTemplateVersion(templateId, 1, "en", "v1-content");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE incident.notice_template_versions SET content = 'changed' WHERE id = ?",
                versionId))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("P0001"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM incident.notice_template_versions WHERE id = ?",
                versionId))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("P0001"));
    }

    @Test
    void templateVersionUniqueConstraint() {
        UUID templateId = insertNoticeTemplate();
        insertTemplateVersion(templateId, 1, "en", "v1-content");

        assertThatThrownBy(() -> insertTemplateVersion(templateId, 1, "en", "duplicate"))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("23505"));
    }

    @Test
    void draftIdempotencyUniqueConstraint() {
        UUID templateId = insertNoticeTemplate();
        UUID versionId = insertTemplateVersion(templateId, 1, "en", "v1-content");
        UUID incidentId = insertIncidentCase();

        insertNoticeDraft(incidentId, versionId, "AUTHORITY_NOTICE");

        assertThatThrownBy(() -> insertNoticeDraft(incidentId, versionId, "AUTHORITY_NOTICE"))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("23505"));
    }

    @Test
    void approvalIdempotencyUniqueConstraint() {
        UUID templateId = insertNoticeTemplate();
        UUID versionId = insertTemplateVersion(templateId, 1, "en", "v1-content");
        UUID incidentId = insertIncidentCase();
        UUID draftId = insertNoticeDraft(incidentId, versionId, "AUTHORITY_NOTICE");
        UUID approvalRequestId = UUID.randomUUID();

        insertNoticeApproval(approvalRequestId, draftId, "REQUESTED");

        assertThatThrownBy(() -> insertNoticeApproval(approvalRequestId, draftId, "REQUESTED"))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("23505"));
    }

    @Test
    void dispatchIdempotencyUniqueConstraint() {
        UUID templateId = insertNoticeTemplate();
        UUID versionId = insertTemplateVersion(templateId, 1, "en", "v1-content");
        UUID incidentId = insertIncidentCase();
        UUID draftId = insertNoticeDraft(incidentId, versionId, "AUTHORITY_NOTICE");

        insertDispatchLog(draftId, "authority@example.com", "EMAIL");

        assertThatThrownBy(() -> insertDispatchLog(draftId, "authority@example.com", "EMAIL"))
                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("23505"));
    }

    @Test
    void escalationIdempotencyUniqueConstraint() {
        UUID incidentId = insertIncidentCase();

        insertEscalation(incidentId, 24);

        assertThatThrownBy(() -> insertEscalation(incidentId, 24))
                                .satisfies(ex -> assertThat(extractSqlState(ex)).isEqualTo("23505"));
    }

        private static String extractSqlState(Throwable throwable) {
                Throwable current = throwable;
                while (current != null) {
                        if (current instanceof SQLException sqlException) {
                                return sqlException.getSQLState();
                        }
                        current = current.getCause();
                }
                return null;
        }

    private UUID insertNoticeTemplate() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.notice_templates (tenant_id, template_type, name, created_by) " +
                        "VALUES (?, 'AUTHORITY_NOTICE', 'Authority Template', 'tester') RETURNING id",
                UUID.class,
                tenantId
        );
    }

    private UUID insertTemplateVersion(UUID templateId, int version, String language, String content) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.notice_template_versions " +
                        "(tenant_id, template_id, version, language, content, content_sha256, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class,
                tenantId,
                templateId,
                version,
                language,
                content,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "tester"
        );
    }

    private UUID insertIncidentCase() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.incident_cases " +
                        "(tenant_id, incident_type, status, severity, notify_due_at) " +
                        "VALUES (?, 'DATA_BREACH', 'OPENED', 'HIGH', NOW()) RETURNING id",
                UUID.class,
                tenantId
        );
    }

    private UUID insertNoticeDraft(UUID incidentId, UUID templateVersionId, String noticeType) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.notice_drafts " +
                        "(tenant_id, incident_id, notice_type, template_version_id, language, rendered_content, " +
                        "rendered_sha256, status, created_by) " +
                        "VALUES (?, ?, ?, ?, 'en', 'rendered', ?, 'DRAFT', 'tester') RETURNING id",
                UUID.class,
                tenantId,
                incidentId,
                noticeType,
                templateVersionId,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );
    }

    private UUID insertNoticeApproval(UUID approvalRequestId, UUID draftId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.notice_approvals " +
                        "(tenant_id, approval_request_id, draft_id, status, requested_by) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                UUID.class,
                tenantId,
                approvalRequestId,
                draftId,
                status,
                "tester"
        );
    }

    private UUID insertDispatchLog(UUID draftId, String recipientIdentifier, String channel) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.notice_dispatch_logs " +
                        "(tenant_id, draft_id, recipient_type, recipient_identifier, channel, status, dispatch_payload_sha256) " +
                        "VALUES (?, ?, 'AUTHORITY', ?, ?, 'QUEUED', ?) RETURNING id",
                UUID.class,
                tenantId,
                draftId,
                recipientIdentifier,
                channel,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
    }

    private UUID insertEscalation(UUID incidentId, int thresholdHours) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO incident.incident_escalations " +
                        "(tenant_id, incident_id, threshold_hours, status) " +
                        "VALUES (?, ?, ?, 'CREATED') RETURNING id",
                UUID.class,
                tenantId,
                incidentId,
                thresholdHours
        );
    }
}
