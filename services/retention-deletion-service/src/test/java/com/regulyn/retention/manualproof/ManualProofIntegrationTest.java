package com.regulyn.retention.manualproof;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.config.TestSecurityNoMockConfig;
import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionManualProofTask;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionManualProofTaskStatus;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.model.ManualProofDecisionRequest;
import com.regulyn.retention.model.ManualProofRequest;
import com.regulyn.retention.model.ManualProofTaskResponse;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionManualProofTaskRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import com.regulyn.retention.service.ManualProofArtifactStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test-security-nomock", inheritProfiles = false)
@Import(TestSecurityNoMockConfig.class)
@Testcontainers
public class ManualProofIntegrationTest {

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
    private DeletionManualProofTaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

        @MockBean
        private ManualProofArtifactStore artifactStore;

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
                reset(artifactStore);
                jdbcTemplate.update("DELETE FROM deletion.audit_events WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("UPDATE deletion.deletion_system_execution SET manual_proof_task_id = NULL WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM deletion.deletion_manual_proof_task WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM deletion.deletion_system_execution WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM deletion.deletion_execution_plan WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM deletion.deletion_requests WHERE tenant_id = ?", tenantId);
    }

    @Test
    void manualProofRequestIsIdempotent() {
        DeletionSystemExecution execution = createExecution();

        ManualProofRequest request = new ManualProofRequest();
        request.setReason("Connector proof unavailable");

        ResponseEntity<ManualProofTaskResponse> response1 = restTemplate.exchange(
                url(execution) + "/manual-proof/request",
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                ManualProofTaskResponse.class
        );
        ResponseEntity<ManualProofTaskResponse> response2 = restTemplate.exchange(
                url(execution) + "/manual-proof/request",
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                ManualProofTaskResponse.class
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
        assertThat(response1.getBody().getTaskId()).isEqualTo(response2.getBody().getTaskId());

        assertThat(taskRepository.findByTenantIdAndExecutionId(tenantId, execution.getExecutionId())).isPresent();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_REQUESTED
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_REQUESTED
        );

        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void manualProofSubmitCreatesArtifactAndUpdatesTask() {
        byte[] proofBytes = "proof data".getBytes();
        String expectedHash = sha256(proofBytes);

        DeletionSystemExecution execution = createExecution();
        requestManualProof(execution);
        String expectedStorageRef = "manual-proofs/" + tenantId + "/" + execution.getExecutionId() + "/proof.txt";
        when(artifactStore.store(eq(tenantId), eq(execution.getExecutionId()), anyString(), any(byte[].class), nullable(String.class)))
                .thenReturn(expectedStorageRef);

        stubFor(post(urlEqualTo("/evidence"))
                .withRequestBody(matchingJsonPath("$.metadata.storageRef", equalTo(expectedStorageRef)))
                .withRequestBody(matchingJsonPath("$.metadata.sha256", equalTo(expectedHash)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactId\":\"" + UUID.randomUUID() + "\"}")));

        HttpEntity<MultiValueMap<String, Object>> entity = buildMultipart("proof.txt", proofBytes);

        ResponseEntity<ManualProofTaskResponse> response = restTemplate.exchange(
                url(execution) + "/manual-proof/submit",
                HttpMethod.POST,
                entity,
                ManualProofTaskResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, execution.getExecutionId()).orElseThrow();
        assertThat(task.getTaskStatus()).isEqualTo(DeletionManualProofTaskStatus.SUBMITTED);
        assertThat(task.getSubmissionArtifactId()).isNotNull();
        assertThat(task.getSubmissionHashSha256()).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_SUBMITTED
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_SUBMITTED
        );
        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void manualProofSubmitEvidenceUnavailableDoesNotUpdateTask() {
        DeletionSystemExecution execution = createExecution();
        requestManualProof(execution);
        String expectedStorageRef = "manual-proofs/" + tenantId + "/" + execution.getExecutionId() + "/proof.txt";
        when(artifactStore.store(eq(tenantId), eq(execution.getExecutionId()), anyString(), any(byte[].class), nullable(String.class)))
                .thenReturn(expectedStorageRef);

        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        HttpEntity<MultiValueMap<String, Object>> entity = buildMultipart("proof.txt", "proof data".getBytes());

        ResponseEntity<String> response = restTemplate.exchange(
                url(execution) + "/manual-proof/submit",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, execution.getExecutionId()).orElseThrow();
        assertThat(task.getTaskStatus()).isEqualTo(DeletionManualProofTaskStatus.OPEN);
        assertThat(task.getSubmissionArtifactId()).isNull();
    }

    @Test
    void manualProofSubmitStoreFailureDoesNotCallEvidence() {
        DeletionSystemExecution execution = createExecution();
        requestManualProof(execution);

        when(artifactStore.store(eq(tenantId), eq(execution.getExecutionId()), anyString(), any(byte[].class), nullable(String.class)))
                .thenThrow(new RuntimeException("store failed"));

        HttpEntity<MultiValueMap<String, Object>> entity = buildMultipart("proof.txt", "proof data".getBytes());

        ResponseEntity<String> response = restTemplate.exchange(
                url(execution) + "/manual-proof/submit",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        com.github.tomakehurst.wiremock.client.WireMock.verify(0, postRequestedFor(urlEqualTo("/evidence")));

        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, execution.getExecutionId()).orElseThrow();
        assertThat(task.getTaskStatus()).isEqualTo(DeletionManualProofTaskStatus.OPEN);
        assertThat(task.getSubmissionArtifactId()).isNull();
    }

    @Test
    void manualProofApproveSetsExecutionSucceeded() {
        DeletionSystemExecution execution = createExecution();
        requestManualProof(execution);
        String expectedStorageRef = "manual-proofs/" + tenantId + "/" + execution.getExecutionId() + "/proof.txt";
        when(artifactStore.store(eq(tenantId), eq(execution.getExecutionId()), anyString(), any(byte[].class), nullable(String.class)))
                .thenReturn(expectedStorageRef);

        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactId\":\"" + UUID.randomUUID() + "\"}")));

        HttpEntity<MultiValueMap<String, Object>> entity = buildMultipart("proof.txt", "proof data".getBytes());
        restTemplate.exchange(
                url(execution) + "/manual-proof/submit",
                HttpMethod.POST,
                entity,
                ManualProofTaskResponse.class
        );

        ManualProofDecisionRequest decision = new ManualProofDecisionRequest();
        decision.setDecision("APPROVE");
        decision.setComment("Looks good");

        ResponseEntity<ManualProofTaskResponse> response = restTemplate.exchange(
                url(execution) + "/manual-proof/decide",
                HttpMethod.POST,
                new HttpEntity<>(decision, headers()),
                ManualProofTaskResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, execution.getExecutionId()).orElseThrow();
        DeletionSystemExecution updatedExecution = executionRepository.findById(execution.getExecutionId()).orElseThrow();

        assertThat(task.getTaskStatus()).isEqualTo(DeletionManualProofTaskStatus.APPROVED);
        assertThat(updatedExecution.getExecutionStatus()).isEqualTo(DeletionSystemExecutionStatus.SUCCEEDED);
        assertThat(updatedExecution.getProofArtifactId()).isEqualTo(task.getSubmissionArtifactId());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_APPROVED
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_APPROVED
        );
        Integer systemAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_SYSTEM_SUCCEEDED
        );
        Integer systemOutboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_SYSTEM_SUCCEEDED
        );

        assertThat(auditCount).isNotNull();
        assertThat(outboxCount).isNotNull();
        assertThat(systemAuditCount).isNotNull();
        assertThat(systemOutboxCount).isNotNull();
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
        assertThat(systemAuditCount).isEqualTo(1);
        assertThat(systemOutboxCount).isEqualTo(1);
    }

    private String url(DeletionSystemExecution execution) {
        return "http://localhost:" + port + "/deletions/" + execution.getDeletionId() + "/systems/" + execution.getExecutionId();
    }

        private String sha256(byte[] bytes) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(bytes);
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hash) {
                                sb.append(String.format("%02x", b));
                        }
                        return sb.toString();
                } catch (Exception e) {
                        throw new IllegalStateException("Failed to compute hash", e);
                }
        }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void requestManualProof(DeletionSystemExecution execution) {
        ManualProofRequest request = new ManualProofRequest();
        request.setReason("Need manual proof");
        restTemplate.exchange(
                url(execution) + "/manual-proof/request",
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                ManualProofTaskResponse.class
        );
    }

    private HttpEntity<MultiValueMap<String, Object>> buildMultipart(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        return new HttpEntity<>(body, headers);
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
        plan.setIdempotencyKey("plan-proof-" + UUID.randomUUID());
        plan.setPlanHashSha256("a".repeat(64));
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