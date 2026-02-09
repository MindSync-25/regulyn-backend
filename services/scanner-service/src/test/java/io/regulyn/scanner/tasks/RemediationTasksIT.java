package io.regulyn.scanner.tasks;

import com.regulyn.scanner.ScannerServiceApplication;
import io.regulyn.scanner.TestSecurityConfig;
import io.regulyn.scanner.dto.TaskGenerationRequest;
import io.regulyn.scanner.dto.TaskGenerationResponse;
import io.regulyn.scanner.dto.TaskTransitionRequest;
import io.regulyn.scanner.model.RemediationTaskEntity;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.repository.RemediationTaskEventRepository;
import io.regulyn.scanner.repository.RemediationTaskRepository;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScanSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(
    classes = {ScannerServiceApplication.class, TestSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public class RemediationTasksIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("scanner_tasks_test")
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
    private RemediationTaskRepository remediationTaskRepository;

    @Autowired
    private RemediationTaskEventRepository remediationTaskEventRepository;

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
        remediationTaskEventRepository.deleteAll();
        remediationTaskRepository.deleteAll();
        scanFindingRepository.deleteAll();
        scanRunRepository.deleteAll();
        scanSourceRepository.deleteAll();
        wireMock.resetAll();
    }

    private HttpHeaders headers(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", TENANT_ID.toString());
        headers.set("X-User-ID", userId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void generateTasks_isIdempotent_noDuplicates() {
        ScanSource source = createSource("https://example.com", Map.of(
            "allowedTrackerDomains", List.of("allowed.com")
        ));
        ScanRun run = createRun(source.getSourceId(), "SUCCEEDED");

        insertFinding(run.getRunId(), kindAttributes("INSECURE_FORM_ACTION_HTTP", Map.of("formAction", "http://example.com/submit")));
        insertFinding(run.getRunId(), kindAttributes("FORM_PII_DETECTED", Map.of("formAction", "https://example.com/submit")));
        insertFinding(run.getRunId(), kindAttributes("TRACKER_DETECTED", Map.of("trackerDomain", "tracker.com", "vendor", "Vendor")));
        insertFinding(run.getRunId(), kindAttributes("UNKNOWN_THIRD_PARTY_ENDPOINT", Map.of("domain", "third.com")));

        TaskGenerationRequest request = new TaskGenerationRequest();
        HttpEntity<TaskGenerationRequest> entity = new HttpEntity<>(request, headers(UUID.randomUUID()));

        ResponseEntity<TaskGenerationResponse> first = restTemplate.exchange(
            "/scanner/runs/" + run.getRunId() + "/tasks/generate",
            HttpMethod.POST,
            entity,
            TaskGenerationResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().getCreatedCount()).isEqualTo(4);

        ResponseEntity<TaskGenerationResponse> second = restTemplate.exchange(
            "/scanner/runs/" + run.getRunId() + "/tasks/generate",
            HttpMethod.POST,
            entity,
            TaskGenerationResponse.class
        );

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().getCreatedCount()).isZero();

        Integer taskCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.remediation_tasks WHERE run_id = ?",
            Integer.class,
            run.getRunId()
        );
        assertThat(taskCount).isEqualTo(4);

        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.remediation_task_events WHERE event_type = 'TASK_CREATED'",
            Integer.class
        );
        assertThat(eventCount).isEqualTo(4);

        assertEventCounts("audit_events", "action", "scanner.task_created_from_finding", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.task_created_from_finding", 1);
    }

    @Test
    void taskTransition_writesEvent_andAuditOutbox() {
        ScanSource source = createSource("https://example.com", Map.of());
        ScanRun run = createRun(source.getSourceId(), "SUCCEEDED");
        insertFinding(run.getRunId(), kindAttributes("INSECURE_FORM_ACTION_HTTP", Map.of("formAction", "http://example.com/submit")));

        ResponseEntity<TaskGenerationResponse> generated = generateTasks(run.getRunId());
        UUID taskId = generated.getBody().getCreatedTaskIds().get(0);

        TaskTransitionRequest transitionRequest = new TaskTransitionRequest();
        transitionRequest.setToStatus(TaskTransitionRequest.Status.IN_PROGRESS);
        transitionRequest.setNotes("starting work");

        HttpEntity<TaskTransitionRequest> entity = new HttpEntity<>(transitionRequest, headers(UUID.randomUUID()));

        ResponseEntity<String> response = restTemplate.exchange(
            "/scanner/tasks/" + taskId + "/transition",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        RemediationTaskEntity task = remediationTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(RemediationTaskEntity.Status.IN_PROGRESS);

        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.remediation_task_events WHERE task_id = ? AND event_type = 'STATUS_CHANGED'",
            Integer.class,
            taskId
        );
        assertThat(eventCount).isEqualTo(1);

        assertEventCounts("audit_events", "action", "scanner.task_status_changed", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.task_status_changed", 1);
    }

    @Test
    void closeTask_requiresClosureNotes_andHashes() {
        stubEvidenceCreate();
        ScanSource source = createSource("https://example.com", Map.of());
        ScanRun run = createRun(source.getSourceId(), "SUCCEEDED");
        insertFinding(run.getRunId(), kindAttributes("INSECURE_FORM_ACTION_HTTP", Map.of("formAction", "http://example.com/submit")));

        ResponseEntity<TaskGenerationResponse> generated = generateTasks(run.getRunId());
        UUID taskId = generated.getBody().getCreatedTaskIds().get(0);

        TaskTransitionRequest inProgress = new TaskTransitionRequest();
        inProgress.setToStatus(TaskTransitionRequest.Status.IN_PROGRESS);
        HttpEntity<TaskTransitionRequest> inProgressEntity = new HttpEntity<>(inProgress, headers(UUID.randomUUID()));
        restTemplate.exchange(
            "/scanner/tasks/" + taskId + "/transition",
            HttpMethod.POST,
            inProgressEntity,
            String.class
        );

        String closureNotes = "Issue resolved";
        TaskTransitionRequest closeRequest = new TaskTransitionRequest();
        closeRequest.setToStatus(TaskTransitionRequest.Status.CLOSED);
        closeRequest.setClosureNotes(closureNotes);

        UUID actorId = UUID.randomUUID();
        HttpEntity<TaskTransitionRequest> closeEntity = new HttpEntity<>(closeRequest, headers(actorId));

        ResponseEntity<String> response = restTemplate.exchange(
            "/scanner/tasks/" + taskId + "/transition",
            HttpMethod.POST,
            closeEntity,
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        RemediationTaskEntity task = remediationTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(RemediationTaskEntity.Status.CLOSED);
        assertThat(task.getClosedAt()).isNotNull();
        assertThat(task.getClosedByUserId()).isEqualTo(actorId);
        assertThat(task.getClosureNotesHash()).isEqualTo(sha256(closureNotes));

        assertEventCounts("audit_events", "action", "scanner.task_status_changed", 2);
        assertEventCounts("outbox_events", "event_type", "scanner.task_status_changed", 2);
    }

    private void stubEvidenceCreate() {
        wireMock.stubFor(post(urlPathEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"00000000-0000-0000-0000-000000000111\"}")));
        wireMock.stubFor(post(urlPathEqualTo("/evidence/bundles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bundleId\":\"00000000-0000-0000-0000-000000000222\"}")));
    }

    @Test
    void partialUniqueIndex_allowsClosedDuplicate_butBlocksOpenDuplicate() {
        ScanSource source = createSource("https://example.com", Map.of());
        ScanRun run = createRun(source.getSourceId(), "SUCCEEDED");
        String fingerprint = sha256("dup");
        insertFinding(run.getRunId(), kindAttributes("INSECURE_FORM_ACTION_HTTP", Map.of("formAction", "http://example.com/submit")), fingerprint);

        RemediationTaskEntity openTask = new RemediationTaskEntity();
        openTask.setTenantId(TENANT_ID);
        openTask.setSourceId(source.getSourceId());
        openTask.setRunId(run.getRunId());
        openTask.setFindingPk(null);
        openTask.setFindingFingerprint(fingerprint);
        openTask.setTitle("Fix insecure form submission (HTTP)");
        openTask.setSeverity(RemediationTaskEntity.Severity.HIGH);
        openTask.setStatus(RemediationTaskEntity.Status.OPEN);
        openTask.setDueDate(LocalDate.now(ZoneOffset.UTC).plusDays(14));
        remediationTaskRepository.save(openTask);

        ResponseEntity<TaskGenerationResponse> first = generateTasks(run.getRunId());
        assertThat(first.getBody().getCreatedCount()).isZero();

        openTask.setStatus(RemediationTaskEntity.Status.CLOSED);
        openTask.setClosedAt(Instant.now());
        openTask.setClosedByUserId(UUID.randomUUID());
        remediationTaskRepository.save(openTask);

        TaskGenerationRequest request = new TaskGenerationRequest();
        request.setForceReopenClosed(true);
        HttpEntity<TaskGenerationRequest> entity = new HttpEntity<>(request, headers(UUID.randomUUID()));

        ResponseEntity<TaskGenerationResponse> second = restTemplate.exchange(
            "/scanner/runs/" + run.getRunId() + "/tasks/generate",
            HttpMethod.POST,
            entity,
            TaskGenerationResponse.class
        );

        assertThat(second.getBody().getCreatedCount()).isEqualTo(1);

        Integer taskCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.remediation_tasks WHERE finding_fingerprint = ?",
            Integer.class,
            fingerprint
        );
        assertThat(taskCount).isEqualTo(2);
    }

    private ResponseEntity<TaskGenerationResponse> generateTasks(UUID runId) {
        TaskGenerationRequest request = new TaskGenerationRequest();
        HttpEntity<TaskGenerationRequest> entity = new HttpEntity<>(request, headers(UUID.randomUUID()));
        return restTemplate.exchange(
            "/scanner/runs/" + runId + "/tasks/generate",
            HttpMethod.POST,
            entity,
            TaskGenerationResponse.class
        );
    }

    private ScanSource createSource(String baseUrl, Map<String, Object> metadata) {
        ScanSource source = new ScanSource();
        source.setTenantId(TENANT_ID);
        source.setSourceName("Website Source");
        source.setSystemId(UUID.randomUUID());
        source.setSourceType("WEBSITE");
        source.setStatus("ACTIVE");
        source.setBaseUrl(baseUrl);
        source.setAuthType("NONE");
        source.setAuthRef(null);
        source.setMetadata(metadata != null ? metadata : Map.of());
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
        insertFinding(runId, keyAttributes, null);
    }

    private void insertFinding(UUID runId, Map<String, Object> keyAttributes, String fingerprintOverride) {
        ScanFinding finding = new ScanFinding();
        finding.setTenantId(TENANT_ID);
        finding.setRunId(runId);
        finding.setFindingType("FIELD");
        finding.setEntityType("WEB_PAGE");
        finding.setRiskLevel("MED");
        finding.setConfidence(90);
        finding.setKeyAttributes(keyAttributes);
        finding.setDetails(Map.of("websiteFindingKind", keyAttributes.get("kind")));
        finding.setFindingFingerprint(fingerprintOverride != null ? fingerprintOverride : sha256(UUID.randomUUID().toString()));
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
