package com.regulyn.retention.exceptions;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.config.TestSecurityNoMockConfig;
import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionBackupExceptionType;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.model.DeletionExceptionGrantRequest;
import com.regulyn.retention.model.DeletionExceptionResponse;
import com.regulyn.retention.model.TombstoneCreateRequest;
import com.regulyn.retention.model.TombstoneRemoveRequest;
import com.regulyn.retention.model.TombstoneResponse;
import com.regulyn.retention.repository.DeletionBackupExceptionRepository;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import com.regulyn.retention.repository.DeletionTombstoneRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test-security-nomock", inheritProfiles = false)
@Import(TestSecurityNoMockConfig.class)
@Testcontainers
public class ExceptionAndTombstoneIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("deletion")
            .withUsername("test")
            .withPassword("test");

    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private DeletionExecutionPlanRepository planRepository;

    @Autowired
    private DeletionSystemExecutionRepository executionRepository;

    @Autowired
    private DeletionBackupExceptionRepository exceptionRepository;

    @Autowired
    private DeletionTombstoneRepository tombstoneRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("audit.schema", () -> "deletion");
        registry.add("retention.scheduler.enabled", () -> "false");
    }

    @AfterAll
    static void shutdownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
                jdbcTemplate.update("DELETE FROM deletion.audit_events WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
                exceptionRepository.deleteAll();
                executionRepository.deleteAll();
                planRepository.deleteAll();
                tombstoneRepository.deleteAll();
                deletionRequestRepository.deleteAll();
    }

    @Test
    void exceptionGrantRequiresArtifact() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactId\":\"" + UUID.randomUUID() + "\"}")));

        DeletionSystemExecution execution = createExecution();

        DeletionExceptionGrantRequest request = new DeletionExceptionGrantRequest();
        request.setExceptionType(DeletionBackupExceptionType.BACKUP_RETENTION);
        request.setReason("Backup retention window");
        request.setNotBefore(Instant.parse("2026-03-01T00:00:00Z"));

        ResponseEntity<DeletionExceptionResponse> response = restTemplate.exchange(
                exceptionUrl(execution),
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                DeletionExceptionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exceptionRepository.findAll()).hasSize(1);

        DeletionSystemExecution updatedExecution = executionRepository.findById(execution.getExecutionId()).orElseThrow();
        assertThat(updatedExecution.getExecutionStatus()).isEqualTo(DeletionSystemExecutionStatus.EXCEPTION_GRANTED);
        assertThat(updatedExecution.getExceptionArtifactId()).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_EXCEPTION_GRANTED
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_EXCEPTION_GRANTED
        );
        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void exceptionGrantEvidenceUnavailableDoesNotUpdate() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        DeletionSystemExecution execution = createExecution();

        DeletionExceptionGrantRequest request = new DeletionExceptionGrantRequest();
        request.setExceptionType(DeletionBackupExceptionType.LEGAL_HOLD);
        request.setReason("Legal hold");
        request.setNotBefore(Instant.parse("2026-03-01T00:00:00Z"));

        ResponseEntity<String> response = restTemplate.exchange(
                exceptionUrl(execution),
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exceptionRepository.findAll()).isEmpty();
    }

    @Test
    void tombstoneCreateAndRemoveRequireEvidence() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactId\":\"" + UUID.randomUUID() + "\"}")));

        TombstoneCreateRequest createRequest = new TombstoneCreateRequest();
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setSubjectRef("user-123");
        createRequest.setReason("Deletion completed");

        ResponseEntity<TombstoneResponse> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/tombstones",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers()),
                TombstoneResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().getCreatedArtifactId()).isNotNull();
        assertThat(tombstoneRepository.findAll()).hasSize(1);

        TombstoneRemoveRequest removeRequest = new TombstoneRemoveRequest();
        removeRequest.setReason("Retention policy update");

        ResponseEntity<TombstoneResponse> removeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/tombstones/" + createResponse.getBody().getTombstoneId() + "/remove",
                HttpMethod.POST,
                new HttpEntity<>(removeRequest, headers()),
                TombstoneResponse.class
        );

        assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removeResponse.getBody()).isNotNull();
        assertThat(removeResponse.getBody().getRemovedArtifactId()).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action IN (?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_TOMBSTONED,
                DeletionCascadeAuditActions.DELETION_TOMBSTONE_REMOVED
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type IN (?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_TOMBSTONED,
                DeletionCascadeEventTypes.DELETION_TOMBSTONE_REMOVED
        );
        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
        assertThat(auditCount).isEqualTo(2);
        assertThat(outboxCount).isEqualTo(2);
    }

    @Test
    void tombstoneCreateEvidenceUnavailableDoesNotPersist() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        TombstoneCreateRequest createRequest = new TombstoneCreateRequest();
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setSubjectRef("user-999");
        createRequest.setReason("Deletion completed");

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/tombstones",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(tombstoneRepository.findAll()).isEmpty();
    }

    private String exceptionUrl(DeletionSystemExecution execution) {
        return "http://localhost:" + port + "/deletions/" + execution.getDeletionId()
                + "/systems/" + execution.getExecutionId() + "/exceptions/grant";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private DeletionSystemExecution createExecution() {
        DeletionRequest deletion = new DeletionRequest();
        deletion.setTenantId(tenantId);
        deletion.setSubjectId(UUID.randomUUID());
        deletion.setSubjectType("CUSTOMER");
        deletion.setEntityType("USER_PROFILE");
        deletion.setSource("DSAR");
        deletion.setStatus("APPROVED");
        deletion.setRequiresApproval(true);
        deletion.setApprovedBy(actorId);
        deletion.setApprovedAt(Instant.now());
        deletion = deletionRequestRepository.save(deletion);

        DeletionExecutionPlan plan = new DeletionExecutionPlan();
        plan.setTenantId(tenantId);
        plan.setDeletionId(deletion.getDeletionId());
        plan.setIdempotencyKey("plan-exception-" + UUID.randomUUID());
        plan.setPlanHashSha256("b".repeat(64));
        plan.setPlanJson("{\"steps\":[]}");
        plan = planRepository.save(plan);

        DeletionSystemExecution execution = new DeletionSystemExecution();
        execution.setTenantId(tenantId);
        execution.setDeletionId(deletion.getDeletionId());
        execution.setPlanId(plan.getPlanId());
        execution.setSystemKey("crm");
        execution.setSubjectRef("user-123");
        execution.setExecutionStatus(DeletionSystemExecutionStatus.RUNNING);
        execution = executionRepository.save(execution);

        return execution;
    }
}