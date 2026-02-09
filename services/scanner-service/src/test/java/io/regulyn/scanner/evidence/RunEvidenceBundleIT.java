package io.regulyn.scanner.evidence;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.regulyn.scanner.ScannerServiceApplication;
import io.regulyn.scanner.TestSecurityConfig;
import io.regulyn.scanner.dto.RunEvidenceBundleResponse;
import io.regulyn.scanner.dto.TaskGenerationRequest;
import io.regulyn.scanner.dto.TaskGenerationResponse;
import io.regulyn.scanner.dto.TaskTransitionRequest;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.model.ScannedPageEntity;
import io.regulyn.scanner.repository.RemediationTaskRepository;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunEvidenceRefRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScanSourceRepository;
import io.regulyn.scanner.repository.ScannedPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {ScannerServiceApplication.class, TestSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public class RunEvidenceBundleIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("scanner_run_bundle_test")
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
    private ScannedPageRepository scannedPageRepository;

    @Autowired
    private RemediationTaskRepository remediationTaskRepository;

    @Autowired
    private ScanRunEvidenceRefRepository scanRunEvidenceRefRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("retention.baseUrl", () -> "http://localhost:0");
        registry.add("evidence.baseUrl", () -> "http://localhost:" + wireMock.getPort());
    }

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE audit_events, outbox_events RESTART IDENTITY CASCADE");
        scanRunEvidenceRefRepository.deleteAll();
        remediationTaskRepository.deleteAll();
        scannedPageRepository.deleteAll();
        scanFindingRepository.deleteAll();
        scanRunRepository.deleteAll();
        scanSourceRepository.deleteAll();
        wireMock.resetAll();
    }

    @Test
    void runEvidenceBundle_includesFindingsTasksProofs_andIsIdempotent() {
        stubEvidenceCreate("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002");
        stubBundleCreate("00000000-0000-0000-0000-000000000999");

        ScanSource source = createSource("https://example.com");
        ScanRun run = createRun(source.getSourceId(), "SUCCEEDED");
        insertFinding(run.getRunId(), kindAttributes("INSECURE_FORM_ACTION_HTTP", Map.of("formAction", "http://example.com/submit")));
        insertFinding(run.getRunId(), kindAttributes("UNKNOWN_THIRD_PARTY_ENDPOINT", Map.of("domain", "third.com")));
        insertPage(run.getRunId(), source.getSourceId());

        TaskGenerationResponse generated = generateTasks(run.getRunId());
        UUID taskId = generated.getCreatedTaskIds().get(0);

        TaskTransitionRequest closeRequest = new TaskTransitionRequest();
        closeRequest.setToStatus(TaskTransitionRequest.Status.CLOSED);
        closeRequest.setClosureNotes("Fixed issue");
        restTemplate.exchange(
            "/scanner/tasks/" + taskId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, headers(UUID.randomUUID())),
            String.class
        );

        ResponseEntity<RunEvidenceBundleResponse> first = restTemplate.exchange(
            "/scanner/runs/" + run.getRunId() + "/evidence/bundle",
            HttpMethod.POST,
            new HttpEntity<>(headers(UUID.randomUUID())),
            RunEvidenceBundleResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().getStatus()).isEqualTo(RunEvidenceBundleResponse.Status.CREATED);

        ResponseEntity<RunEvidenceBundleResponse> second = restTemplate.exchange(
            "/scanner/runs/" + run.getRunId() + "/evidence/bundle",
            HttpMethod.POST,
            new HttpEntity<>(headers(UUID.randomUUID())),
            RunEvidenceBundleResponse.class
        );

        assertThat(second.getBody().getStatus()).isEqualTo(RunEvidenceBundleResponse.Status.ALREADY_EXISTS);
        assertThat(scanRunEvidenceRefRepository.findByTenantIdAndRunId(TENANT_ID, run.getRunId())).isPresent();

        assertEventCounts("audit_events", "action", "scanner.scan_evidence_bundle_created", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.scan_evidence_bundle_created", 1);

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/evidence/bundles")));

        String bundleBody = findEvidenceRequestBody("SCAN_RUN_EVIDENCE_SNAPSHOT");
        assertThat(bundleBody).contains("\"runId\"");
        assertThat(bundleBody).contains("\"findings\"");
        assertThat(bundleBody).contains("\"tasks\"");
        assertThat(bundleBody).contains("\"evidenceArtifactRef\"");
    }

    private TaskGenerationResponse generateTasks(UUID runId) {
        TaskGenerationRequest request = new TaskGenerationRequest();
        HttpEntity<TaskGenerationRequest> entity = new HttpEntity<>(request, headers(UUID.randomUUID()));
        ResponseEntity<TaskGenerationResponse> response = restTemplate.exchange(
            "/scanner/runs/" + runId + "/tasks/generate",
            HttpMethod.POST,
            entity,
            TaskGenerationResponse.class
        );
        return response.getBody();
    }

    private void insertPage(UUID runId, UUID sourceId) {
        ScannedPageEntity page = new ScannedPageEntity();
        page.setTenantId(TENANT_ID);
        page.setSourceId(sourceId);
        page.setRunId(runId);
        page.setUrl("https://example.com");
        page.setUrlHash(sha256("https://example.com"));
        page.setDepth(0);
        page.setHttpStatus(200);
        page.setTitle("Home");
        scannedPageRepository.save(page);
    }

    private ScanSource createSource(String baseUrl) {
        ScanSource source = new ScanSource();
        source.setTenantId(TENANT_ID);
        source.setSourceName("Website Source");
        source.setSystemId(UUID.randomUUID());
        source.setSourceType("WEBSITE");
        source.setStatus("ACTIVE");
        source.setBaseUrl(baseUrl);
        source.setAuthType("NONE");
        source.setMetadata(Map.of("maxDepth", 1));
        return scanSourceRepository.save(source);
    }

    private ScanRun createRun(UUID sourceId, String status) {
        ScanRun run = new ScanRun();
        run.setTenantId(TENANT_ID);
        run.setSourceId(sourceId);
        run.setScanMode("BOTH");
        run.setStatus(status);
        run.setFindingsCount(0);
        return scanRunRepository.save(run);
    }

    private void insertFinding(UUID runId, Map<String, Object> keyAttributes) {
        ScanFinding finding = new ScanFinding();
        finding.setTenantId(TENANT_ID);
        finding.setRunId(runId);
        finding.setFindingType("FIELD");
        finding.setEntityType("WEB_PAGE");
        finding.setRiskLevel("MED");
        finding.setConfidence(90);
        finding.setKeyAttributes(keyAttributes);
        finding.setDetails(Map.of("websiteFindingKind", keyAttributes.get("kind")));
        finding.setFindingFingerprint(sha256(UUID.randomUUID().toString()));
        finding.setFindingFingerprintVersion((short) 1);
        scanFindingRepository.save(finding);
    }

    private Map<String, Object> kindAttributes(String kind, Map<String, Object> extra) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("kind", kind);
        if (extra != null) {
            attributes.putAll(extra);
        }
        return attributes;
    }

    private HttpHeaders headers(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", TENANT_ID.toString());
        headers.set("X-User-ID", userId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void stubEvidenceCreate(String firstEvidenceId, String secondEvidenceId) {
        wireMock.stubFor(post(urlPathEqualTo("/evidence"))
            .inScenario("evidence")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + firstEvidenceId + "\"}"))
            .willSetStateTo("SECOND"));

        wireMock.stubFor(post(urlPathEqualTo("/evidence"))
            .inScenario("evidence")
            .whenScenarioStateIs("SECOND")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + secondEvidenceId + "\"}")));
    }

    private void stubBundleCreate(String bundleId) {
        wireMock.stubFor(post(urlPathEqualTo("/evidence/bundles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bundleId\":\"" + bundleId + "\"}")));
    }

    private String findEvidenceRequestBody(String type) {
        return wireMock.getAllServeEvents().stream()
            .filter(event -> event.getRequest().getUrl().equals("/evidence"))
            .map(event -> event.getRequest().getBodyAsString())
            .filter(body -> body.contains("\"type\":\"" + type + "\""))
            .findFirst()
            .orElse("");
    }

    private void assertEventCounts(String table, String column, String value, int minExpected) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
            Integer.class,
            value
        );
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(minExpected);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
