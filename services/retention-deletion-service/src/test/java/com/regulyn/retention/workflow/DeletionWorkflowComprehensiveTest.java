package com.regulyn.retention.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.retention.config.TestSecurityConfig;
import com.regulyn.retention.entity.DeletionProof;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.RetentionCandidate;
import com.regulyn.retention.entity.RetentionRule;
import com.regulyn.retention.model.*;
import com.regulyn.retention.repository.DeletionProofRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.RetentionCandidateRepository;
import com.regulyn.retention.repository.RetentionRuleRepository;
import com.regulyn.retention.service.RetentionScheduler;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TestSecurityConfig.class)
public class DeletionWorkflowComprehensiveTest {

    private static final Logger log = LoggerFactory.getLogger(DeletionWorkflowComprehensiveTest.class);

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
    private DeletionProofRepository deletionProofRepository;

    @Autowired
    private RetentionRuleRepository retentionRuleRepository;

    @Autowired
    private RetentionCandidateRepository retentionCandidateRepository;

    @Autowired(required = false)
    private RetentionScheduler retentionScheduler;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID testTenantId = UUID.randomUUID();
    private final UUID testUserId = UUID.randomUUID();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // WireMock server for evidence service
        wireMockServer = new WireMockServer(8083);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8083);
        
        registry.add("evidence.service.url", () -> "http://localhost:8083");
    }

    @AfterAll
    static void cleanup() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Reset WireMock before each test
        wireMockServer.resetAll();
        
        // Default stubs for evidence service (success scenarios)
        stubEvidenceServiceSuccess();
    }

    private void stubEvidenceServiceSuccess() {
        // Stub POST /evidence
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));

        // Stub POST /bundles
        wireMockServer.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\":\"" + UUID.randomUUID() + "\"}")));
    }

    private void stubEvidenceServiceUnavailable() {
        // Override with 503 responses
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));

        wireMockServer.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));
    }

    private HttpHeaders createHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", testTenantId.toString());
        headers.set("X-User-ID", testUserId.toString());
        if (idempotencyKey != null) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Idempotency - Same key returns same deletionId without duplicate")
    void testIdempotency() {
        // Arrange
        String idempotencyKey = "test-idempotency-" + UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        
        CreateDeletionRequest request = new CreateDeletionRequest();
        request.setSubjectId(subjectId);
        request.setSubjectType("CUSTOMER");
        request.setEntityType("USER_PROFILE");
        request.setSource("DSAR");
        request.setReason("Test idempotency");
        request.setRequiresApproval(true);
        request.setProofRequired(true);
        request.setDueInDays(30);

        HttpHeaders headers = createHeaders(idempotencyKey);
        HttpEntity<CreateDeletionRequest> entity = new HttpEntity<>(request, headers);

        // Act - First request
        ResponseEntity<CreateDeletionResponse> response1 = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                entity,
                CreateDeletionResponse.class
        );

        // Act - Second request with same idempotency key
        ResponseEntity<CreateDeletionResponse> response2 = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                entity,
                CreateDeletionResponse.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
        
        // Same deletionId returned
        assertThat(response1.getBody().getDeletionId()).isEqualTo(response2.getBody().getDeletionId());
        
        // Only one deletion_request row exists
        List<DeletionRequest> deletions = deletionRequestRepository.findByTenantIdAndSubjectId(
                testTenantId, subjectId);
        assertThat(deletions).hasSize(1);
        assertThat(deletions.get(0).getDeletionId()).isEqualTo(response1.getBody().getDeletionId());
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Approval Gating - Cannot transition to IN_PROGRESS without approval")
    void testApprovalGating() throws Exception {
        // Arrange - Create deletion with requiresApproval=true
        CreateDeletionRequest createRequest = new CreateDeletionRequest();
        createRequest.setSubjectId(UUID.randomUUID());
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setEntityType("USER_PROFILE");
        createRequest.setSource("DSAR");
        createRequest.setRequiresApproval(true);
        createRequest.setProofRequired(false);

        HttpHeaders headers = createHeaders(null);
        HttpEntity<CreateDeletionRequest> createEntity = new HttpEntity<>(createRequest, headers);

        ResponseEntity<CreateDeletionResponse> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                createEntity,
                CreateDeletionResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID deletionId = createResponse.getBody().getDeletionId();

        // First assign to move to IN_REVIEW
        AssignDeletionRequest assignRequest = new AssignDeletionRequest();
        assignRequest.setAssignedTo(testUserId);
        
        HttpEntity<AssignDeletionRequest> assignEntity = new HttpEntity<>(assignRequest, headers);
        ResponseEntity<AssignDeletionResponse> assignResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/assign",
                HttpMethod.POST,
                assignEntity,
                AssignDeletionResponse.class
        );
        
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act - Attempt to transition to IN_PROGRESS before approval
        TransitionDeletionRequest transitionRequest = new TransitionDeletionRequest();
        transitionRequest.setToStatus("IN_PROGRESS");
        transitionRequest.setReason("Attempting without approval");

        HttpEntity<TransitionDeletionRequest> transitionEntity = new HttpEntity<>(transitionRequest, headers);
        
        // This should fail with 409 CONFLICT - TestRestTemplate returns ResponseEntity with error status
        ResponseEntity<String> failedTransition = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                transitionEntity,
                String.class
        );
        
        // Verify 409 CONFLICT
        assertThat(failedTransition.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Act - Approve the deletion
        ApproveDeletionRequest approveRequest = new ApproveDeletionRequest();
        approveRequest.setDecision("APPROVE");
        approveRequest.setReason("Approved for testing");

        HttpEntity<ApproveDeletionRequest> approveEntity = new HttpEntity<>(approveRequest, headers);
        ResponseEntity<ApproveDeletionResponse> approveResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/approve",
                HttpMethod.POST,
                approveEntity,
                ApproveDeletionResponse.class
        );

        // Assert - Approval succeeds
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody().getStatus()).isEqualTo("APPROVED");

        // Act - Now transition to IN_PROGRESS should succeed
        ResponseEntity<TransitionDeletionResponse> successTransition = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                transitionEntity,
                TransitionDeletionResponse.class
        );

        // Assert - Transition succeeds
        assertThat(successTransition.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(successTransition.getBody().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Proof Required - Cannot close without proof upload")
    void testProofRequired() throws Exception {
        // Arrange - Create deletion with proofRequired=true
        CreateDeletionRequest createRequest = new CreateDeletionRequest();
        createRequest.setSubjectId(UUID.randomUUID());
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setEntityType("USER_PROFILE");
        createRequest.setSource("DSAR");
        createRequest.setRequiresApproval(true);
        createRequest.setProofRequired(true);

        HttpHeaders headers = createHeaders(null);
        HttpEntity<CreateDeletionRequest> createEntity = new HttpEntity<>(createRequest, headers);

        ResponseEntity<CreateDeletionResponse> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                createEntity,
                CreateDeletionResponse.class
        );

        UUID deletionId = createResponse.getBody().getDeletionId();

        // Assign and approve
        AssignDeletionRequest assignRequest = new AssignDeletionRequest();
        assignRequest.setAssignedTo(testUserId);
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/assign",
                HttpMethod.POST,
                new HttpEntity<>(assignRequest, headers),
                AssignDeletionResponse.class
        );

        ApproveDeletionRequest approveRequest = new ApproveDeletionRequest();
        approveRequest.setDecision("APPROVE");
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(approveRequest, headers),
                ApproveDeletionResponse.class
        );

        // Transition to COMPLETED
        TransitionDeletionRequest transitionRequest = new TransitionDeletionRequest();
        transitionRequest.setToStatus("IN_PROGRESS");
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest, headers),
                TransitionDeletionResponse.class
        );

        transitionRequest.setToStatus("COMPLETED");
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest, headers),
                TransitionDeletionResponse.class
        );

        // Act - Attempt to close without proof upload
        CloseDeletionRequest closeRequest = new CloseDeletionRequest();
        closeRequest.setClosureNotes("Attempting to close without proof");

        // This should fail with 409 CONFLICT - TestRestTemplate returns ResponseEntity with error status
        ResponseEntity<String> failedClose = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/close",
                HttpMethod.POST,
                new HttpEntity<>(closeRequest, headers),
                String.class
        );
        
        // Verify 409 CONFLICT
        assertThat(failedClose.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Verify no proofs exist
        long proofCount = deletionProofRepository.countByDeletionId(deletionId);
        assertThat(proofCount).isEqualTo(0);

        // Act - Upload proof
        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.set("X-Tenant-ID", testTenantId.toString());
        multipartHeaders.set("X-User-ID", testUserId.toString());
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("Test proof content".getBytes()) {
            @Override
            public String getFilename() {
                return "deletion-proof.txt";
            }
        };
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> proofEntity = new HttpEntity<>(body, multipartHeaders);

        ResponseEntity<UploadProofResponse> proofResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/proofs",
                HttpMethod.POST,
                proofEntity,
                UploadProofResponse.class
        );

        // Assert - Proof uploaded successfully
        assertThat(proofResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(proofResponse.getBody().getProofId()).isNotNull();
        assertThat(proofResponse.getBody().getArtifactHash()).isNotNull();

        // Verify proof exists in database
        proofCount = deletionProofRepository.countByDeletionId(deletionId);
        assertThat(proofCount).isEqualTo(1);

        // Act - Now close should succeed
        ResponseEntity<CloseDeletionResponse> closeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/close",
                HttpMethod.POST,
                new HttpEntity<>(closeRequest, headers),
                CloseDeletionResponse.class
        );

        // Assert - Close succeeds and evidence bundle created
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody().getStatus()).isEqualTo("CLOSED");
        assertThat(closeResponse.getBody().getEvidenceBundleId()).isNotNull();

        // Verify evidence_bundle_id stored in database
        DeletionRequest deletion = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, testTenantId)
                .orElseThrow();
        assertThat(deletion.getEvidenceBundleId()).isNotNull();

        // Verify WireMock received calls
        wireMockServer.verify(postRequestedFor(urlEqualTo("/evidence")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/bundles")));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Evidence Service Unavailable - Close returns 503")
    void testEvidenceServiceUnavailable() throws Exception {
        // Arrange - Create and complete a deletion
        CreateDeletionRequest createRequest = new CreateDeletionRequest();
        createRequest.setSubjectId(UUID.randomUUID());
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setEntityType("USER_PROFILE");
        createRequest.setSource("DSAR");
        createRequest.setRequiresApproval(false);
        createRequest.setProofRequired(false);

        HttpHeaders headers = createHeaders(null);
        ResponseEntity<CreateDeletionResponse> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                CreateDeletionResponse.class
        );

        UUID deletionId = createResponse.getBody().getDeletionId();

        // Transition to COMPLETED
        TransitionDeletionRequest transitionRequest = new TransitionDeletionRequest();
        transitionRequest.setToStatus("IN_PROGRESS");
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest, headers),
                TransitionDeletionResponse.class
        );

        transitionRequest.setToStatus("COMPLETED");
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(transitionRequest, headers),
                TransitionDeletionResponse.class
        );

        // Act - Configure WireMock to return 503
        stubEvidenceServiceUnavailable();

        // Attempt to close
        CloseDeletionRequest closeRequest = new CloseDeletionRequest();
        closeRequest.setClosureNotes("Attempting to close with evidence service down");

        ResponseEntity<Map> closeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/close",
                HttpMethod.POST,
                new HttpEntity<>(closeRequest, headers),
                Map.class
        );

        // Assert - Should return 503 Service Unavailable
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(closeResponse.getBody()).containsKey("error");
        assertThat(closeResponse.getBody().get("error").toString())
                .containsIgnoringCase("Evidence service unavailable");

        // Assert - Deletion should NOT be marked as CLOSED
        DeletionRequest deletion = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, testTenantId)
                .orElseThrow();
        assertThat(deletion.getStatus()).isEqualTo("COMPLETED"); // Still in COMPLETED
        assertThat(deletion.getEvidenceBundleId()).isNull(); // No bundle ID stored
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Scheduler Auto-Create - Creates deletion with idempotency")
    void testSchedulerAutoCreate() {
        // Skip test if scheduler is not available (e.g., disabled via property)
        if (retentionScheduler == null) {
            log.warn("Skipping scheduler test - RetentionScheduler bean not available");
            return;
        }
        
        // Arrange - Create retention rule
        RetentionRule rule = new RetentionRule();
        rule.setTenantId(testTenantId);
        rule.setRuleName("Test Auto-Delete Rule " + UUID.randomUUID());
        rule.setSubjectType("CUSTOMER");
        rule.setEntityType("USER_PROFILE");
        rule.setRetentionDays(1); // 1 day retention
        rule.setAction("DELETE");
        rule.setEnabled(true);
        rule.setCreatedBy(testUserId);
        
        RetentionRule savedRule = retentionRuleRepository.save(rule);

        // Create candidate with lastSeenAt older than 1 day
        UUID candidateSubjectId = UUID.randomUUID();
        RetentionCandidate candidate = new RetentionCandidate();
        candidate.setTenantId(testTenantId);
        candidate.setSubjectId(candidateSubjectId);
        candidate.setSubjectType("CUSTOMER");
        candidate.setEntityType("USER_PROFILE");
        candidate.setLastSeenAt(Instant.now().minus(2, ChronoUnit.DAYS)); // 2 days ago
        
        RetentionCandidate savedCandidate = retentionCandidateRepository.save(candidate);

        // Verify no deletion exists yet
        List<DeletionRequest> beforeDeletions = deletionRequestRepository.findByTenantIdAndSubjectId(
                testTenantId, candidateSubjectId);
        assertThat(beforeDeletions).isEmpty();

        // Act - Invoke scheduler directly
        retentionScheduler.processRetentionCandidates();

        // Assert - Deletion created with source=RETENTION
        List<DeletionRequest> afterDeletions = deletionRequestRepository.findByTenantIdAndSubjectId(
                testTenantId, candidateSubjectId);
        assertThat(afterDeletions).hasSize(1);
        
        DeletionRequest autoDeletion = afterDeletions.get(0);
        assertThat(autoDeletion.getSource()).isEqualTo("RETENTION");
        assertThat(autoDeletion.getStatus()).isEqualTo("REQUESTED");
        assertThat(autoDeletion.getReason()).contains("Auto-created from retention rule");
        assertThat(autoDeletion.getReason()).contains(savedRule.getRuleName());
        assertThat(autoDeletion.getIdempotencyKey())
                .isEqualTo("retention-" + savedRule.getRuleId() + "-" + savedCandidate.getCandidateId());

        // Act - Invoke scheduler again
        retentionScheduler.processRetentionCandidates();

        // Assert - No duplicate created (idempotent auto-create)
        List<DeletionRequest> finalDeletions = deletionRequestRepository.findByTenantIdAndSubjectId(
                testTenantId, candidateSubjectId);
        assertThat(finalDeletions).hasSize(1); // Still only 1
        assertThat(finalDeletions.get(0).getDeletionId()).isEqualTo(autoDeletion.getDeletionId());
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Full Happy Path - Create to Close with all steps")
    void testFullHappyPath() throws Exception {
        // This test validates the complete workflow from creation to closure
        
        // Create
        CreateDeletionRequest createRequest = new CreateDeletionRequest();
        createRequest.setSubjectId(UUID.randomUUID());
        createRequest.setSubjectType("CUSTOMER");
        createRequest.setEntityType("USER_PROFILE");
        createRequest.setSource("DSAR");
        createRequest.setReason("Full workflow test");
        createRequest.setRequiresApproval(true);
        createRequest.setProofRequired(true);

        HttpHeaders headers = createHeaders("happy-path-" + UUID.randomUUID());
        
        ResponseEntity<CreateDeletionResponse> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                CreateDeletionResponse.class
        );
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID deletionId = createResponse.getBody().getDeletionId();
        assertThat(createResponse.getBody().getStatus()).isEqualTo("REQUESTED");

        // Assign
        AssignDeletionRequest assignRequest = new AssignDeletionRequest();
        assignRequest.setAssignedTo(testUserId);
        
        ResponseEntity<AssignDeletionResponse> assignResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/assign",
                HttpMethod.POST,
                new HttpEntity<>(assignRequest, headers),
                AssignDeletionResponse.class
        );
        
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Approve
        ApproveDeletionRequest approveRequest = new ApproveDeletionRequest();
        approveRequest.setDecision("APPROVE");
        approveRequest.setReason("Approved for testing full workflow");
        
        ResponseEntity<ApproveDeletionResponse> approveResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(approveRequest, headers),
                ApproveDeletionResponse.class
        );
        
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody().getStatus()).isEqualTo("APPROVED");

        // Transition to IN_PROGRESS
        TransitionDeletionRequest progressRequest = new TransitionDeletionRequest();
        progressRequest.setToStatus("IN_PROGRESS");
        progressRequest.setReason("Starting deletion process");
        
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(progressRequest, headers),
                TransitionDeletionResponse.class
        );

        // Transition to COMPLETED
        TransitionDeletionRequest completeRequest = new TransitionDeletionRequest();
        completeRequest.setToStatus("COMPLETED");
        completeRequest.setReason("Deletion completed");
        
        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/transition",
                HttpMethod.POST,
                new HttpEntity<>(completeRequest, headers),
                TransitionDeletionResponse.class
        );

        // Upload proof
        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.set("X-Tenant-ID", testTenantId.toString());
        multipartHeaders.set("X-User-ID", testUserId.toString());
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("Happy path proof content".getBytes()) {
            @Override
            public String getFilename() {
                return "happy-path-proof.pdf";
            }
        };
        body.add("file", fileResource);

        restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/proofs",
                HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders),
                UploadProofResponse.class
        );

        // Close
        CloseDeletionRequest closeRequest = new CloseDeletionRequest();
        closeRequest.setClosureNotes("Full workflow test completed successfully");
        
        ResponseEntity<CloseDeletionResponse> closeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletionId + "/close",
                HttpMethod.POST,
                new HttpEntity<>(closeRequest, headers),
                CloseDeletionResponse.class
        );

        // Final assertions
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody().getStatus()).isEqualTo("CLOSED");
        assertThat(closeResponse.getBody().getEvidenceBundleId()).isNotNull();

        // Verify final state in database
        DeletionRequest finalDeletion = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, testTenantId)
                .orElseThrow();
        
        assertThat(finalDeletion.getStatus()).isEqualTo("CLOSED");
        assertThat(finalDeletion.getAssignedTo()).isEqualTo(testUserId);
        assertThat(finalDeletion.getApprovedBy()).isEqualTo(testUserId);
        assertThat(finalDeletion.getApprovedAt()).isNotNull();
        assertThat(finalDeletion.getClosedAt()).isNotNull();
        assertThat(finalDeletion.getEvidenceBundleId()).isNotNull();
        
        // Verify proof stored
        List<DeletionProof> proofs = deletionProofRepository.findByDeletionId(deletionId);
        assertThat(proofs).hasSize(1);
        assertThat(proofs.get(0).getFilename()).isEqualTo("happy-path-proof.pdf");
    }
}
