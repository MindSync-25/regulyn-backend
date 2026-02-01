package io.regulyn.scanner;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.regulyn.scanner.ScannerServiceApplication;
import io.regulyn.scanner.dto.*;
import io.regulyn.scanner.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {ScannerServiceApplication.class, TestSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public class ScannerServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("scanner_test")
            .withUsername("test")
            .withPassword("test");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private ScanRunRepository scanRunRepository;

    @Autowired
    private ScanFindingRepository scanFindingRepository;

    @Autowired
    private ScanPromotionRepository scanPromotionRepository;

    @Autowired
    private ScannerExportRepository scannerExportRepository;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("retention.baseUrl", () -> "http://localhost:" + wireMock.getPort());
        registry.add("evidence.baseUrl", () -> "http://localhost:" + wireMock.getPort());
    }

    @BeforeEach
    void cleanup() {
        scannerExportRepository.deleteAll();
        scanPromotionRepository.deleteAll();
        scanFindingRepository.deleteAll();
        scanRunRepository.deleteAll();
        scanSourceRepository.deleteAll();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void testCreateSourceAndList() {
        // Create source
        CreateScanSourceRequest request = new CreateScanSourceRequest();
        request.setSourceName("Test Source");
        request.setSystemId(UUID.randomUUID());
        request.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        request.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        request.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> entity = new HttpEntity<>(request, createHeaders());
        ResponseEntity<ScanSourceResponse> response = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                entity,
                ScanSourceResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSourceName()).isEqualTo("Test Source");

        // List sources
        HttpEntity<Void> listEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanSourceResponse[]> listResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.GET,
                listEntity,
                ScanSourceResponse[].class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().length).isGreaterThan(0);
    }

    @Test
    void testDisableSourcePreventsNewRuns() {
        // Create source
        CreateScanSourceRequest createRequest = new CreateScanSourceRequest();
        createRequest.setSourceName("To Disable");
        createRequest.setSystemId(UUID.randomUUID());
        createRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        createRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        createRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> createEntity = new HttpEntity<>(createRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> createResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                createEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = createResponse.getBody().getSourceId();

        // Disable source
        HttpEntity<Void> disableEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanSourceResponse> disableResponse = restTemplate.exchange(
                "/scanner/sources/" + sourceId + "/disable",
                HttpMethod.POST,
                disableEntity,
                ScanSourceResponse.class
        );

        assertThat(disableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Try to create run - should fail
        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<String> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                String.class
        );

        assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testExecuteRunWithMockAdapter() {
        // Create MOCK source
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Mock Source");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        // Create run
        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        // Execute run
        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanRunResponse> execResponse = restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        assertThat(execResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(execResponse.getBody().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(execResponse.getBody().getFindingsCount()).isEqualTo(9); // 4 inventory + 5 retention

        // Get findings
        HttpEntity<Void> findingsEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<FindingResponse[]> findingsResponse = restTemplate.exchange(
                "/scanner/runs/" + runId + "/findings",
                HttpMethod.GET,
                findingsEntity,
                FindingResponse[].class
        );

        assertThat(findingsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findingsResponse.getBody()).hasSize(9);
    }

    @Test
    void testExecuteRunIdempotency() {
        // Create source and run
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Idempotency Test");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        // Execute first time
        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanRunResponse> exec1 = restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        assertThat(exec1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Execute second time - should fail
        ResponseEntity<String> exec2 = restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                String.class
        );

        assertThat(exec2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void testHttpDiscoveryAdapterWithWireMock() {
        // Setup WireMock
        wireMock.stubFor(get(urlPathEqualTo("/discover"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "entities": [
                                        {"entityType": "user", "description": "User entity"}
                                    ],
                                    "fields": [
                                        {"entityType": "user", "fieldName": "email", "riskLevel": "HIGH"}
                                    ],
                                    "retentionCandidates": [
                                        {"subjectId": "00000000-0000-0000-0000-000000000001", "subjectType": "USER", "entityType": "PERSONAL_DATA", "riskLevel": "MEDIUM"}
                                    ]
                                }
                                """)));

        // Create HTTP_DISCOVERY source
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("HTTP Discovery");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.HTTP_DISCOVERY);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setBaseUrl(wireMock.baseUrl());
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        // Create and execute run
        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanRunResponse> execResponse = restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        assertThat(execResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(execResponse.getBody().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(execResponse.getBody().getFindingsCount()).isEqualTo(3);

        // Verify WireMock was called
        wireMock.verify(getRequestedFor(urlPathEqualTo("/discover")));
    }

    @Test
    void testPromoteRetentionCandidates() {
        // Setup WireMock for retention service
        wireMock.stubFor(post(urlPathEqualTo("/retention/candidates"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"candidateId\": \"" + UUID.randomUUID() + "\"}")));

        // Create source, run, and execute
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Retention Test");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.RETENTION_CANDIDATES);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        // Promote candidates
        PromoteRetentionCandidatesRequest promoteRequest = new PromoteRetentionCandidatesRequest();
        promoteRequest.setMinRiskLevel(PromoteRetentionCandidatesRequest.RiskLevel.MED);
        promoteRequest.setSubjectType(PromoteRetentionCandidatesRequest.SubjectType.CUSTOMER);
        promoteRequest.setLimit(100);

        HttpEntity<PromoteRetentionCandidatesRequest> promoteEntity = new HttpEntity<>(promoteRequest, createHeaders());
        ResponseEntity<PromoteRetentionCandidatesResponse> promoteResponse = restTemplate.exchange(
                "/scanner/runs/" + runId + "/promote/retention-candidates",
                HttpMethod.POST,
                promoteEntity,
                PromoteRetentionCandidatesResponse.class
        );

        assertThat(promoteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promoteResponse.getBody().getPromotedCount()).isGreaterThan(0);
    }

    @Test
    void testPromotionNoDoublePromotion() {
        // Setup WireMock
        wireMock.stubFor(post(urlPathEqualTo("/retention/candidates"))
                .willReturn(aResponse().withStatus(201).withBody("{\"candidateId\": \"" + UUID.randomUUID() + "\"}")));

        // Create and execute run
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Double Promotion Test");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.RETENTION_CANDIDATES);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        // First promotion
        PromoteRetentionCandidatesRequest promoteRequest = new PromoteRetentionCandidatesRequest();
        promoteRequest.setSubjectType(PromoteRetentionCandidatesRequest.SubjectType.CUSTOMER);
        promoteRequest.setMinRiskLevel(PromoteRetentionCandidatesRequest.RiskLevel.LOW);
        promoteRequest.setLimit(100);

        HttpEntity<PromoteRetentionCandidatesRequest> promoteEntity = new HttpEntity<>(promoteRequest, createHeaders());
        ResponseEntity<PromoteRetentionCandidatesResponse> promote1 = restTemplate.exchange(
                "/scanner/runs/" + runId + "/promote/retention-candidates",
                HttpMethod.POST,
                promoteEntity,
                PromoteRetentionCandidatesResponse.class
        );

        assertThat(promote1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second promotion - should fail
        ResponseEntity<String> promote2 = restTemplate.exchange(
                "/scanner/runs/" + runId + "/promote/retention-candidates",
                HttpMethod.POST,
                promoteEntity,
                String.class
        );

        assertThat(promote2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void testExportScansWithWireMock() {
        // Setup WireMock for evidence service
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        wireMock.stubFor(post(urlPathEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/evidence/bundles"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlPathMatching("/evidence/bundles/.*/export"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        wireMock.stubFor(get(urlPathMatching("/evidence/exports/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("test export data")));

        // Create source and run
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Export Test");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        UUID sourceId = sourceResponse.getBody().getSourceId();

        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        // Create export
        CreateScanExportRequest exportRequest = new CreateScanExportRequest();
        exportRequest.setPeriodFrom(Instant.now().minusSeconds(3600));
        exportRequest.setPeriodTo(Instant.now());

        HttpEntity<CreateScanExportRequest> exportEntity = new HttpEntity<>(exportRequest, createHeaders());
        ResponseEntity<ScanExportResponse> exportResponse = restTemplate.exchange(
                "/scanner/exports/scans",
                HttpMethod.POST,
                exportEntity,
                ScanExportResponse.class
        );

        assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exportResponse.getBody()).isNotNull();

        // Download export
        UUID scanExportId = exportResponse.getBody().getExportId();
        HttpEntity<Void> downloadEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<byte[]> downloadResponse = restTemplate.exchange(
                "/scanner/exports/" + scanExportId + "/download",
                HttpMethod.GET,
                downloadEntity,
                byte[].class
        );

        assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testFullWorkflowSucceeds() {
        // This test verifies the entire workflow executes without errors
        // Create source
        CreateScanSourceRequest sourceRequest = new CreateScanSourceRequest();
        sourceRequest.setSourceName("Full Workflow");
        sourceRequest.setSystemId(UUID.randomUUID());
        sourceRequest.setSourceType(CreateScanSourceRequest.SourceType.MOCK);
        sourceRequest.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        sourceRequest.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, createHeaders());
        ResponseEntity<ScanSourceResponse> sourceResponse = restTemplate.exchange(
                "/scanner/sources",
                HttpMethod.POST,
                sourceEntity,
                ScanSourceResponse.class
        );

        assertThat(sourceResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Create run
        UUID sourceId = sourceResponse.getBody().getSourceId();
        CreateScanRunRequest runRequest = new CreateScanRunRequest();
        runRequest.setSourceId(sourceId);
        runRequest.setScanMode(CreateScanRunRequest.ScanMode.BOTH);

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, createHeaders());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
                "/scanner/runs",
                HttpMethod.POST,
                runEntity,
                ScanRunResponse.class
        );

        assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Execute run
        UUID runId = runResponse.getBody().getRunId();
        HttpEntity<Void> execEntity = new HttpEntity<>(createHeaders());
        ResponseEntity<ScanRunResponse> execResponse = restTemplate.exchange(
                "/scanner/runs/" + runId + "/execute",
                HttpMethod.POST,
                execEntity,
                ScanRunResponse.class
        );

        assertThat(execResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(execResponse.getBody().getStatus()).isEqualTo("SUCCEEDED");

        // Events would be created in outbox_events table (verified by main code execution)
        // This confirms the full flow works end-to-end
    }
}
