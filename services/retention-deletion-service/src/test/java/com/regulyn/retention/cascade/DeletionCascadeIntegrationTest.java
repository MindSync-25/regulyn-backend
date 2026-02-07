package com.regulyn.retention.cascade;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.retention.config.TestSecurityNoMockConfig;
import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.model.CascadeExecuteResponse;
import com.regulyn.retention.persistence.AbstractPostgresIT;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test-security-nomock", inheritProfiles = false)
@Import(TestSecurityNoMockConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeletionCascadeIntegrationTest extends AbstractPostgresIT {

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
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @DynamicPropertySource
    static void configureCascadeProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
                configureFor("localhost", wireMockServer.port());

        registry.add("connector.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("deletion.cascade.systems[0].systemKey", () -> "crm");
        registry.add("deletion.cascade.systems[0].entityTypes[0]", () -> "USER_PROFILE");
        registry.add("retention.scheduler.enabled", () -> "false");
        registry.add("audit.schema", () -> "deletion");
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
    }

    @Test
    @Order(1)
    void cascadeExecuteCreatesPlanExecutionsAndEvents() {
        stubFor(post(urlEqualTo("/connectors/deletions/jobs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"job-123\",\"acceptedAt\":\"2026-02-07T00:00:00Z\"}")));

        DeletionRequest deletion = createApprovedDeletion("USER_PROFILE");

        HttpHeaders headers = createHeaders("cascade-key-1");
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<CascadeExecuteResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletion.getDeletionId() + "/cascade-execute",
                HttpMethod.POST,
                entity,
                CascadeExecuteResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        DeletionExecutionPlan plan = planRepository.findByTenantIdAndIdempotencyKey(tenantId, "cascade-key-1")
                .orElseThrow();
        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());

        assertThat(executions).isNotEmpty();
        assertThat(executions.get(0).getExecutionStatus().name()).isIn("RUNNING", "FAILED_RETRYABLE");

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE action IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThanOrEqualTo(3);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThanOrEqualTo(3);

        verify(postRequestedFor(urlEqualTo("/connectors/deletions/jobs"))
                .withHeader("X-Tenant-ID", equalTo(tenantId.toString()))
                .withHeader("X-User-ID", equalTo(actorId.toString())));
    }

    @Test
    @Order(2)
    void connectorFailureMarksRetryable() {
        stubFor(post(urlEqualTo("/connectors/deletions/jobs"))
                .willReturn(aResponse().withStatus(503)));

        DeletionRequest deletion = createApprovedDeletion("USER_PROFILE");

        HttpHeaders headers = createHeaders("cascade-key-2");
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<CascadeExecuteResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletion.getDeletionId() + "/cascade-execute",
                HttpMethod.POST,
                entity,
                CascadeExecuteResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        DeletionExecutionPlan plan = planRepository.findByTenantIdAndIdempotencyKey(tenantId, "cascade-key-2")
                .orElseThrow();
        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());

        assertThat(executions).isNotEmpty();
        assertThat(executions.get(0).getExecutionStatus().name()).isEqualTo("FAILED_RETRYABLE");
        assertThat(executions.get(0).getNextRetryAt()).isNotNull();
        assertThat(executions.get(0).getAttemptCount()).isGreaterThan(0);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE action = ?",
                Integer.class,
                DeletionCascadeAuditActions.DELETION_SYSTEM_FAILED_RETRYABLE
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = ?",
                Integer.class,
                DeletionCascadeEventTypes.DELETION_SYSTEM_FAILED_RETRYABLE
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    @Order(3)
    void idempotencyDoesNotCreateDuplicates() {
        stubFor(post(urlEqualTo("/connectors/deletions/jobs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"job-456\"}")));

        DeletionRequest deletion = createApprovedDeletion("USER_PROFILE");

        HttpHeaders headers = createHeaders("cascade-key-3");
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        Integer auditBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE action IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED
        );
        Integer outboxBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED
        );

        ResponseEntity<CascadeExecuteResponse> response1 = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletion.getDeletionId() + "/cascade-execute",
                HttpMethod.POST,
                entity,
                CascadeExecuteResponse.class
        );

        Integer auditAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE action IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED
        );
        Integer outboxAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED
        );

        ResponseEntity<CascadeExecuteResponse> response2 = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletion.getDeletionId() + "/cascade-execute",
                HttpMethod.POST,
                entity,
                CascadeExecuteResponse.class
        );

        Integer auditAfterSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE action IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED
        );
        Integer outboxAfterSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type IN (?, ?, ?)",
                Integer.class,
                DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
        assertThat(response1.getBody().getPlanId()).isEqualTo(response2.getBody().getPlanId());

        List<DeletionExecutionPlan> plans = planRepository.findByTenantIdAndDeletionIdOrderByPlanVersionDesc(tenantId, deletion.getDeletionId());
        assertThat(plans).hasSize(1);

        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plans.get(0).getPlanId());
        assertThat(executions).hasSize(1);

                assertThat(auditBefore).isNotNull();
                assertThat(outboxBefore).isNotNull();
                assertThat(auditAfterFirst).isNotNull();
                assertThat(outboxAfterFirst).isNotNull();
                assertThat(auditAfterSecond).isNotNull();
                assertThat(outboxAfterSecond).isNotNull();
                assertThat(auditAfterFirst).isEqualTo(auditBefore + 3);
                assertThat(outboxAfterFirst).isEqualTo(outboxBefore + 3);
                assertThat(auditAfterSecond).isEqualTo(auditAfterFirst);
                assertThat(outboxAfterSecond).isEqualTo(outboxAfterFirst);
    }

    private DeletionRequest createApprovedDeletion(String entityType) {
        DeletionRequest deletion = new DeletionRequest();
        deletion.setTenantId(tenantId);
        deletion.setSubjectId(UUID.randomUUID());
        deletion.setSubjectType("CUSTOMER");
        deletion.setEntityType(entityType);
        deletion.setSource("DSAR");
        deletion.setStatus("APPROVED");
        deletion.setRequiresApproval(true);
        deletion.setApprovedBy(actorId);
        deletion.setApprovedAt(Instant.now());
        return deletionRequestRepository.save(deletion);
    }

    private HttpHeaders createHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        headers.set("X-Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
