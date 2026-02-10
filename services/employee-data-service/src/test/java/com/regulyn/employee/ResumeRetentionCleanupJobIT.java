package com.regulyn.employee;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.regulyn.employee.config.TestSecurityConfig;
import com.regulyn.employee.persistence.entity.EmployeeResume;
import com.regulyn.employee.persistence.entity.EmployeeResumeDeletionExecutionEntity;
import com.regulyn.employee.persistence.repo.EmployeeResumeDeletionExecutionRepository;
import com.regulyn.employee.persistence.repo.EmployeeResumeRepository;
import com.regulyn.employee.scheduler.ResumeRetentionCleanupJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(TestSecurityConfig.class)
class ResumeRetentionCleanupJobIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMock.getPort());
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "employee");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("audit.schema", () -> "employee");
        registry.add("employee.resumeRetention.cleanup.fixedDelay", () -> "PT5M");
        registry.add("employee.resumeRetention.cleanup.batchSize", () -> 50);
    }

    @Autowired
    private ResumeRetentionCleanupJob cleanupJob;

    @Autowired
    private EmployeeResumeRepository resumeRepository;

    @Autowired
    private EmployeeResumeDeletionExecutionRepository executionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM employee.outbox_events");
        jdbcTemplate.update("DELETE FROM employee.audit_events");
        jdbcTemplate.update("DELETE FROM employee.employee_resume_deletion_executions");
        jdbcTemplate.update("DELETE FROM employee.employee_resumes");
        wireMock.resetAll();
    }

    @Test
    void deletes_only_with_proof() {
        UUID tenantId = UUID.randomUUID();
        EmployeeResume resume = createResume(tenantId);

        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactRef\":\"art-1\",\"sha256\":\"hash-1\"}")));

        cleanupJob.cleanupExpiredResumes();

        EmployeeResume updated = resumeRepository.findByTenantIdAndResumeId(tenantId, resume.getResumeId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DELETED");
        assertThat(updated.getDeletionArtifactRef()).isEqualTo("art-1");

        EmployeeResumeDeletionExecutionEntity execution = executionRepository
                .findTopByTenantIdAndResumeIdOrderByAttemptNoDesc(tenantId, resume.getResumeId())
                .orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(EmployeeResumeDeletionExecutionEntity.Status.SUCCEEDED);
        assertThat(execution.getArtifactRef()).isEqualTo("art-1");

        assertThat(countAuditEvents("RESUME_DELETED", resume.getResumeId())).isEqualTo(1);
        assertThat(countOutboxEvents("resume.deleted", resume.getResumeId())).isEqualTo(1);
    }

    @Test
    void evidence_failure_marks_failed_retryable() {
        UUID tenantId = UUID.randomUUID();
        EmployeeResume resume = createResume(tenantId);

        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(aResponse().withStatus(500)));

        cleanupJob.cleanupExpiredResumes();

        EmployeeResume updated = resumeRepository.findByTenantIdAndResumeId(tenantId, resume.getResumeId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FAILED_RETRYABLE");
        assertThat(updated.getDeletionArtifactRef()).isNull();

        EmployeeResumeDeletionExecutionEntity execution = executionRepository
                .findTopByTenantIdAndResumeIdOrderByAttemptNoDesc(tenantId, resume.getResumeId())
                .orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(EmployeeResumeDeletionExecutionEntity.Status.FAILED_RETRYABLE);

        assertThat(countAuditEvents("RESUME_DELETE_FAILED", resume.getResumeId())).isEqualTo(1);
        assertThat(countOutboxEvents("resume.delete_failed", resume.getResumeId())).isEqualTo(1);
    }

    @Test
    void retry_succeeds_after_failure() {
        UUID tenantId = UUID.randomUUID();
        EmployeeResume resume = createResume(tenantId);

        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(aResponse().withStatus(500)));

        cleanupJob.cleanupExpiredResumes();

        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactRef\":\"art-2\",\"sha256\":\"hash-2\"}")));

        cleanupJob.cleanupExpiredResumes();

        EmployeeResume updated = resumeRepository.findByTenantIdAndResumeId(tenantId, resume.getResumeId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DELETED");
        assertThat(updated.getDeletionArtifactRef()).isEqualTo("art-2");

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee.employee_resume_deletion_executions WHERE tenant_id = ? AND resume_id = ?",
                Integer.class,
                tenantId,
                resume.getResumeId()
        );
        assertThat(attempts).isEqualTo(2);

        assertThat(countAuditEvents("RESUME_DELETE_FAILED", resume.getResumeId())).isEqualTo(1);
        assertThat(countAuditEvents("RESUME_DELETED", resume.getResumeId())).isEqualTo(1);
        assertThat(countOutboxEvents("resume.delete_failed", resume.getResumeId())).isEqualTo(1);
        assertThat(countOutboxEvents("resume.deleted", resume.getResumeId())).isEqualTo(1);
    }

    private EmployeeResume createResume(UUID tenantId) {
        EmployeeResume resume = new EmployeeResume();
        resume.setTenantId(tenantId);
        resume.setEmployeeRef("EMP-" + tenantId.toString().substring(0, 8));
        resume.setCollectedAt(OffsetDateTime.now().minusDays(5));
        resume.setSource("HRIS");
        resume.setRetentionDays(30);
        resume.setDeleteAfter(OffsetDateTime.now().minusMinutes(5));
        resume.setStatus("ACTIVE");
        resume.setStorageRef("s3://bucket/resume.pdf");
        resume.setChecksumSha256("deadbeef");
        return resumeRepository.save(resume);
    }

    private int countAuditEvents(String action, UUID resumeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee.audit_events WHERE action = ? AND entity_id = ?",
                Integer.class,
                action,
                resumeId
        );
        return count != null ? count : 0;
    }

    private int countOutboxEvents(String eventType, UUID resumeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee.outbox_events WHERE event_type = ? AND entity_id = ?",
                Integer.class,
                eventType,
                resumeId.toString()
        );
        return count != null ? count : 0;
    }
}
