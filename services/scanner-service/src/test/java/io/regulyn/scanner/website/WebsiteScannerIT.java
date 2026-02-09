package io.regulyn.scanner.website;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.regulyn.scanner.ScannerServiceApplication;
import io.regulyn.scanner.dto.CreateScanRunRequest;
import io.regulyn.scanner.dto.CreateScanSourceRequest;
import io.regulyn.scanner.dto.ScanRunResponse;
import io.regulyn.scanner.dto.ScanSourceResponse;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScanSourceRepository;
import io.regulyn.scanner.repository.ScannedPageRepository;
import io.regulyn.scanner.TestSecurityConfig;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {ScannerServiceApplication.class, TestSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public class WebsiteScannerIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("scanner_web_test")
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
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.execute("TRUNCATE audit_events, outbox_events RESTART IDENTITY CASCADE");
        scannedPageRepository.deleteAll();
        scanFindingRepository.deleteAll();
        scanRunRepository.deleteAll();
        scanSourceRepository.deleteAll();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void crawlerStoresPagesAndFindings_success() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withHeader("Set-Cookie", "_ga=GA1.2.123; Path=/; HttpOnly")
                .withBody("""
                    <html><head><title>Home</title></head>
                    <body>
                      <a href=\"/p1\">p1</a>
                      <form action=\"https://example.com/submit\" method=\"post\">
                        <input type=\"email\" name=\"email\" />
                      </form>
                      <script src=\"https://www.googletagmanager.com/gtm.js\"></script>
                    </body>
                    </html>
                """)));

        wireMock.stubFor(get(urlPathEqualTo("/p1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body><a href=\"/p2\">p2</a></body></html>")));

        wireMock.stubFor(get(urlPathEqualTo("/p2"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>done</body></html>")));

        UUID runId = executeWebsiteRun(buildWebsiteSourceRequest(wireMock.baseUrl(), Map.of(
            "maxDepth", 2,
            "maxPages", 10,
            "totalTimeoutMs", 5000,
            "perRequestTimeoutMs", 1000
        )));

        assertThat(scannedPageRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId)).hasSize(3);

        Integer findingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.scan_findings WHERE run_id = ?",
            Integer.class,
            runId
        );
        assertThat(findingCount).isNotNull();
        assertThat(findingCount).isGreaterThanOrEqualTo(3);

        Integer fingerprintNulls = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scanner.scan_findings WHERE run_id = ? AND finding_fingerprint IS NULL",
            Integer.class,
            runId
        );
        assertThat(fingerprintNulls).isEqualTo(0);

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");

        assertEventCounts("audit_events", "action", "scanner.run_started", 1);
        assertEventCounts("audit_events", "action", "scanner.run_succeeded", 1);
        assertEventCounts("audit_events", "action", "scanner.finding_detected", 1);

        assertEventCounts("outbox_events", "event_type", "scanner.run_started", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.run_succeeded", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.finding_detected", 1);
    }

    @Test
    void crawlerMarksPartial_onTotalTimeout_persistsPartialEvidence() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body><a href=\"/slow\">slow</a></body></html>")));

        wireMock.stubFor(get(urlPathEqualTo("/slow"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>slow</body></html>")));

        UUID runId = executeWebsiteRun(buildWebsiteSourceRequest(wireMock.baseUrl(), Map.of(
            "maxDepth", 2,
            "maxPages", 10,
            "totalTimeoutMs", 50,
            "perRequestTimeoutMs", 1000
        )));

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        assertThat(run.getErrorMessage()).contains("TOTAL_TIMEOUT");

        assertThat(scannedPageRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId)).isNotEmpty();

        assertEventCounts("audit_events", "action", "scanner.run_partial", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.run_partial", 1);
    }

    @Test
    void perRequestTimeout_causesPartial_afterSomeProgress() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body><a href=\"/slow\">slow</a></body></html>")));

        wireMock.stubFor(get(urlPathEqualTo("/slow"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>slow</body></html>")));

        UUID runId = executeWebsiteRun(buildWebsiteSourceRequest(wireMock.baseUrl(), Map.of(
            "maxDepth", 2,
            "maxPages", 10,
            "totalTimeoutMs", 5000,
            "perRequestTimeoutMs", 50,
            "maxFailures", 1
        )));

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        assertThat(run.getErrorMessage()).contains("REQUEST_TIMEOUT");

        assertThat(scannedPageRepository.findByTenantIdAndRunId(UUID.fromString(TENANT_ID), runId)).isNotEmpty();
        assertEventCounts("audit_events", "action", "scanner.run_partial", 1);
        assertEventCounts("outbox_events", "event_type", "scanner.run_partial", 1);
    }

    private UUID executeWebsiteRun(CreateScanSourceRequest sourceRequest) {
        HttpEntity<CreateScanSourceRequest> sourceEntity = new HttpEntity<>(sourceRequest, headers());
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

        HttpEntity<CreateScanRunRequest> runEntity = new HttpEntity<>(runRequest, headers());
        ResponseEntity<ScanRunResponse> runResponse = restTemplate.exchange(
            "/scanner/runs",
            HttpMethod.POST,
            runEntity,
            ScanRunResponse.class
        );

        UUID runId = runResponse.getBody().getRunId();

        HttpEntity<Void> execEntity = new HttpEntity<>(headers());
        restTemplate.exchange(
            "/scanner/runs/" + runId + "/execute",
            HttpMethod.POST,
            execEntity,
            ScanRunResponse.class
        );

        return runId;
    }

    private CreateScanSourceRequest buildWebsiteSourceRequest(String baseUrl, Map<String, Object> metadata) {
        CreateScanSourceRequest request = new CreateScanSourceRequest();
        request.setSourceName("Website Source");
        request.setSystemId(UUID.randomUUID());
        request.setSourceType(CreateScanSourceRequest.SourceType.WEBSITE);
        request.setStatus(CreateScanSourceRequest.SourceStatus.ACTIVE);
        request.setBaseUrl(baseUrl);
        request.setAuthType(CreateScanSourceRequest.AuthType.NONE);

        Map<String, Object> meta = new HashMap<>(metadata);
        request.setMetadata(meta);
        return request;
    }

    private void assertEventCounts(String table, String column, String value, int expected) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
            Integer.class,
            value
        );
        assertThat(count).isEqualTo(expected);
    }
}