package com.regulyn.retention.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.config.TestSecurityNoMockConfig;
import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionBackupExceptionType;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.model.*;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test-security-nomock", inheritProfiles = false)
@Import(TestSecurityNoMockConfig.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullCascadeDeletionE2EIT {

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
    private DeletionExecutionPlanRepository planRepository;

    @Autowired
    private DeletionSystemExecutionRepository executionRepository;

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

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

        registry.add("connector.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("audit.schema", () -> "deletion");
        registry.add("retention.scheduler.enabled", () -> "false");
        registry.add("evidence.storage.manualProofDir", () -> "target/manual-proofs-e2e");

        registry.add("deletion.cascade.systems[0].systemKey", () -> "crm");
        registry.add("deletion.cascade.systems[0].entityTypes[0]", () -> "USER_PROFILE");
        registry.add("deletion.cascade.systems[1].systemKey", () -> "billing");
        registry.add("deletion.cascade.systems[1].entityTypes[0]", () -> "USER_PROFILE");
    }

    @AfterAll
    static void shutdownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        stubConnectorSuccess();
        stubEvidenceSuccess();

        jdbcTemplate.update("DELETE FROM deletion.audit_events WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("UPDATE deletion.deletion_system_execution SET manual_proof_task_id = NULL WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_manual_proof_task WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_backup_exception WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_system_execution WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_execution_plan WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_proofs WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_status_history WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deletion.deletion_requests WHERE tenant_id = ?", tenantId);
    }

    @Test
    @Order(1)
    void proofIncompleteBlocksCompletion() {
        UUID deletionId = createDeletion(true, false);
        assignAndApprove(deletionId);

        ResponseEntity<CascadeExecuteResponse> cascadeResponse = cascadeExecute(deletionId, "cascade-e2e-1");
        assertThat(cascadeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        transition(deletionId, "IN_PROGRESS");

        ResponseEntity<String> failedCompletion = restTemplate.exchange(
                url("/deletions/" + deletionId + "/transition"),
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest("COMPLETED"), headers(null)),
                String.class
        );

        assertThat(failedCompletion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Integer incompleteAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_PROOF_INCOMPLETE
        );
        Integer incompleteOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_PROOF_INCOMPLETE
        );

        assertThat(incompleteAudit).isNotNull();
        assertThat(incompleteOutbox).isNotNull();
        assertThat(incompleteAudit).isGreaterThanOrEqualTo(1);
        assertThat(incompleteOutbox).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(2)
    void manualProofAndExceptionMixedScenario() {
        UUID deletionId = createDeletion(true, false);
        assignAndApprove(deletionId);

        cascadeExecute(deletionId, "cascade-e2e-2");
        DeletionExecutionPlan plan = latestPlan(deletionId);
        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());
        assertThat(executions).hasSize(2);

        transition(deletionId, "IN_PROGRESS");

        DeletionSystemExecution manualExecution = executions.stream()
                .min(Comparator.comparing(DeletionSystemExecution::getSystemKey))
                .orElseThrow();
        DeletionSystemExecution exceptionExecution = executions.stream()
                .max(Comparator.comparing(DeletionSystemExecution::getSystemKey))
                .orElseThrow();

        completeExecutionWithManualProof(deletionId, manualExecution);
        grantExceptionForExecution(deletionId, exceptionExecution);

        DeletionSystemExecution updatedManual = executionRepository.findByTenantIdAndExecutionId(tenantId, manualExecution.getExecutionId()).orElseThrow();
        DeletionSystemExecution updatedException = executionRepository.findByTenantIdAndExecutionId(tenantId, exceptionExecution.getExecutionId()).orElseThrow();

        assertThat(updatedManual.getExecutionStatus()).isEqualTo(DeletionSystemExecutionStatus.SUCCEEDED);
        assertThat(updatedManual.getProofArtifactId()).isNotNull();
        assertThat(updatedException.getExecutionStatus()).isEqualTo(DeletionSystemExecutionStatus.EXCEPTION_GRANTED);
        assertThat(updatedException.getExceptionArtifactId()).isNotNull();

        transition(deletionId, "COMPLETED");

        ResponseEntity<CloseDeletionResponse> closeResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/close"),
                HttpMethod.POST,
                new HttpEntity<>(closeRequest("Closed after proof completeness"), headers(null)),
                CloseDeletionResponse.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody()).isNotNull();

        UUID bundleIdFirst = closeResponse.getBody().getEvidenceBundleId();
        assertThat(bundleIdFirst).isNotNull();

        ResponseEntity<CloseDeletionResponse> closeResponseSecond = restTemplate.exchange(
                url("/deletions/" + deletionId + "/close"),
                HttpMethod.POST,
                new HttpEntity<>(closeRequest("Idempotent close"), headers(null)),
                CloseDeletionResponse.class
        );

        assertThat(closeResponseSecond.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponseSecond.getBody()).isNotNull();
        assertThat(closeResponseSecond.getBody().getEvidenceBundleId()).isEqualTo(bundleIdFirst);

        DeletionRequest persisted = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, tenantId).orElseThrow();
        assertThat(persisted.getEvidenceBundleId()).isEqualTo(bundleIdFirst);

        Integer cascadeAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action IN (?, ?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED
        );
        Integer cascadeOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type IN (?, ?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED
        );

        Integer manualAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action IN (?, ?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_REQUESTED,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_SUBMITTED,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_APPROVED
        );
        Integer manualOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type IN (?, ?, ?)",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_REQUESTED,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_SUBMITTED,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_APPROVED
        );
        Integer exceptionAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_EXCEPTION_GRANTED
        );
        Integer exceptionOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_EXCEPTION_GRANTED
        );
        Integer completedAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                DeletionCascadeAuditActions.DELETION_COMPLETED
        );
        Integer completedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                DeletionCascadeEventTypes.DELETION_COMPLETED
        );
        Integer closedAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deletion.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "deletion.closed"
        );
        Integer closedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "deletion.closed"
        );

        assertThat(cascadeAudit).isNotNull();
        assertThat(cascadeOutbox).isNotNull();
        assertThat(manualAudit).isNotNull();
        assertThat(manualOutbox).isNotNull();
        assertThat(exceptionAudit).isNotNull();
        assertThat(exceptionOutbox).isNotNull();
        assertThat(completedAudit).isNotNull();
        assertThat(completedOutbox).isNotNull();
        assertThat(closedAudit).isNotNull();
        assertThat(closedOutbox).isNotNull();

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/evidence")));
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/bundles")));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/evidence"))
                .withRequestBody(matchingJsonPath("$.metadata.planId", equalTo(plan.getPlanId().toString())))
                .withRequestBody(matchingJsonPath("$.metadata.executionArtifacts[?(@.proofArtifactId == '" + updatedManual.getProofArtifactId() + "')]"))
                .withRequestBody(matchingJsonPath("$.metadata.executionArtifacts[?(@.exceptionArtifactId == '" + updatedException.getExceptionArtifactId() + "')]")));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/bundles"))
                .withRequestBody(matchingJsonPath("$.metadata.planId", equalTo(plan.getPlanId().toString())))
                .withRequestBody(matchingJsonPath("$.metadata.executionArtifacts[?(@.proofArtifactId == '" + updatedManual.getProofArtifactId() + "')]"))
                .withRequestBody(matchingJsonPath("$.metadata.executionArtifacts[?(@.exceptionArtifactId == '" + updatedException.getExceptionArtifactId() + "')]")));
    }

        @Test
        @Order(3)
        void closeFailsWhenEvidenceServiceUnavailable() {
        UUID deletionId = createDeletion(true, false);
        assignAndApprove(deletionId);
        cascadeExecute(deletionId, "cascade-e2e-3");

                DeletionExecutionPlan plan = latestPlan(deletionId);
                List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());
                DeletionSystemExecution manualExecution = executions.stream()
                                .min(Comparator.comparing(DeletionSystemExecution::getSystemKey))
                                .orElseThrow();
                DeletionSystemExecution exceptionExecution = executions.stream()
                                .max(Comparator.comparing(DeletionSystemExecution::getSystemKey))
                                .orElseThrow();

                transition(deletionId, "IN_PROGRESS");
                completeExecutionWithManualProof(deletionId, manualExecution);
                grantExceptionForExecution(deletionId, exceptionExecution);

        transition(deletionId, "COMPLETED");

        stubEvidenceUnavailable();

        ResponseEntity<Map> closeResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/close"),
                HttpMethod.POST,
                new HttpEntity<>(closeRequest("Evidence down"), headers(null)),
                Map.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(closeResponse.getBody()).isNotNull();
        assertThat(closeResponse.getBody().get("error").toString())
                .containsIgnoringCase("Evidence service unavailable");

        DeletionRequest persisted = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, tenantId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("COMPLETED");
        assertThat(persisted.getEvidenceBundleId()).isNull();

        Integer closedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "deletion.closed"
        );
        assertThat(closedOutbox).isNotNull();
        assertThat(closedOutbox).isEqualTo(0);
    }

    private void stubConnectorSuccess() {
        stubFor(post(urlEqualTo("/connectors/deletions/jobs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"job-" + UUID.randomUUID() + "\",\"acceptedAt\":\"2026-02-07T00:00:00Z\"}")));
    }

    private void stubEvidenceSuccess() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactId\":\"" + UUID.randomUUID() + "\",\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));

        stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\":\"" + UUID.randomUUID() + "\"}")));
    }

    private void stubEvidenceUnavailable() {
        stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));

        stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));
    }

    private UUID createDeletion(boolean requiresApproval, boolean proofRequired) {
        CreateDeletionRequest request = new CreateDeletionRequest();
        request.setSubjectId(UUID.randomUUID());
        request.setSubjectType("CUSTOMER");
        request.setEntityType("USER_PROFILE");
        request.setSource("DSAR");
        request.setReason("E2E test");
        request.setRequiresApproval(requiresApproval);
        request.setProofRequired(proofRequired);
        request.setDueInDays(30);

        ResponseEntity<CreateDeletionResponse> response = restTemplate.exchange(
                url("/deletions"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers("e2e-create-" + UUID.randomUUID())),
                CreateDeletionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getDeletionId();
    }

    private void assignAndApprove(UUID deletionId) {
        AssignDeletionRequest assignRequest = new AssignDeletionRequest();
        assignRequest.setAssignedTo(actorId);

        ResponseEntity<AssignDeletionResponse> assignResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/assign"),
                HttpMethod.POST,
                new HttpEntity<>(assignRequest, headers(null)),
                AssignDeletionResponse.class
        );

        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ApproveDeletionRequest approveRequest = new ApproveDeletionRequest();
        approveRequest.setDecision("APPROVE");
        approveRequest.setReason("E2E approval");

        ResponseEntity<ApproveDeletionResponse> approveResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/approve"),
                HttpMethod.POST,
                new HttpEntity<>(approveRequest, headers(null)),
                ApproveDeletionResponse.class
        );

        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<CascadeExecuteResponse> cascadeExecute(UUID deletionId, String idempotencyKey) {
        return restTemplate.exchange(
                url("/deletions/" + deletionId + "/cascade-execute"),
                HttpMethod.POST,
                new HttpEntity<>(null, headers(idempotencyKey)),
                CascadeExecuteResponse.class
        );
    }

    private void transition(UUID deletionId, String status) {
        ResponseEntity<TransitionDeletionResponse> response = restTemplate.exchange(
                url("/deletions/" + deletionId + "/transition"),
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest(status), headers(null)),
                TransitionDeletionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TransitionDeletionRequest transitionRequest(String status) {
        TransitionDeletionRequest request = new TransitionDeletionRequest();
        request.setToStatus(status);
        return request;
    }

    private void completeExecutionWithManualProof(UUID deletionId, DeletionSystemExecution execution) {
        ManualProofRequest proofRequest = new ManualProofRequest();
        proofRequest.setReason("Connector proof missing");
        ResponseEntity<ManualProofTaskResponse> requestResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/systems/" + execution.getExecutionId() + "/manual-proof/request"),
                HttpMethod.POST,
                new HttpEntity<>(proofRequest, headers(null)),
                ManualProofTaskResponse.class
        );

        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ManualProofTaskResponse> submitResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/systems/" + execution.getExecutionId() + "/manual-proof/submit"),
                HttpMethod.POST,
                manualProofMultipart("manual-proof.txt", "manual-proof".getBytes(StandardCharsets.UTF_8)),
                ManualProofTaskResponse.class
        );

        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ManualProofDecisionRequest decisionRequest = new ManualProofDecisionRequest();
        decisionRequest.setDecision("APPROVE");
        decisionRequest.setComment("Approved in E2E");

        ResponseEntity<ManualProofTaskResponse> decisionResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/systems/" + execution.getExecutionId() + "/manual-proof/decide"),
                HttpMethod.POST,
                new HttpEntity<>(decisionRequest, headers(null)),
                ManualProofTaskResponse.class
        );

        assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void grantExceptionForExecution(UUID deletionId, DeletionSystemExecution execution) {
        DeletionExceptionGrantRequest exceptionRequest = new DeletionExceptionGrantRequest();
        exceptionRequest.setExceptionType(DeletionBackupExceptionType.BACKUP_RETENTION);
        exceptionRequest.setReason("Backup retention window");
        exceptionRequest.setNotBefore(Instant.parse("2026-03-01T00:00:00Z"));

        ResponseEntity<DeletionExceptionResponse> exceptionResponse = restTemplate.exchange(
                url("/deletions/" + deletionId + "/systems/" + execution.getExecutionId() + "/exceptions/grant"),
                HttpMethod.POST,
                new HttpEntity<>(exceptionRequest, headers(null)),
                DeletionExceptionResponse.class
        );

        assertThat(exceptionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void uploadDeletionProof(UUID deletionId, String filename, byte[] content) {
        HttpHeaders multipartHeaders = headers(null);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", fileResource);

        ResponseEntity<UploadProofResponse> response = restTemplate.exchange(
                url("/deletions/" + deletionId + "/proofs"),
                HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders),
                UploadProofResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpEntity<MultiValueMap<String, Object>> manualProofMultipart(String filename, byte[] content) {
        HttpHeaders multipartHeaders = headers(null);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", fileResource);
        body.add("notes", "Submitted in E2E");

        return new HttpEntity<>(body, multipartHeaders);
    }

    private CloseDeletionRequest closeRequest(String notes) {
        CloseDeletionRequest request = new CloseDeletionRequest();
        request.setClosureNotes(notes);
        return request;
    }

    private DeletionExecutionPlan latestPlan(UUID deletionId) {
        return planRepository.findByTenantIdAndDeletionIdOrderByPlanVersionDesc(tenantId, deletionId)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        if (idempotencyKey != null) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
