package com.regulyn.ropa.crossborder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.CrossBorderReportExportRequest;
import com.regulyn.ropa.api.dto.CrossBorderReportFilters;
import com.regulyn.ropa.api.dto.CrossBorderTransferUpsertRequest;
import com.regulyn.ropa.model.RopaActivityVersion;
import com.regulyn.ropa.model.RopaDataCategory;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.TransferFrequency;
import com.regulyn.ropa.model.TransferMechanism;
import com.regulyn.ropa.repository.CrossBorderTransferRepository;
import com.regulyn.ropa.repository.RopaActivityVersionRepository;
import com.regulyn.ropa.repository.RopaDataCategoryRepository;
import com.regulyn.ropa.repository.RopaSystemRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class CrossBorderReportExportTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_cross_border_export_test")
        .withUsername("test")
        .withPassword("test");

    private static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "ropa");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "ropa");
        registry.add("audit.schema", () -> "ropa");
        registry.add("evidence.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RopaSystemRepository systemRepository;

    @Autowired
    private RopaActivityVersionRepository activityVersionRepository;

    @Autowired
    private RopaDataCategoryRepository dataCategoryRepository;

    @Autowired
    private CrossBorderTransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000004100");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000004200");

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        transferRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName("CRM");
        system.setSystemType(RopaSystem.SystemType.DATABASE);
        system.setLocation(RopaSystem.Location.INDIA);
        system.setCriticality(RopaSystem.Criticality.HIGH);
        systemRepository.save(system);
        systemId = system.getSystemId();

        RopaActivityVersion activity = new RopaActivityVersion();
        activityId = UUID.randomUUID();
        activity.setTenantId(tenantId);
        activity.setActivityId(activityId);
        activity.setVersionNumber(1);
        activity.setStatus(RopaActivityVersion.Status.PUBLISHED);
        activity.setActivityName("Onboarding");
        activity.setPurpose("KYC");
        activity.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONTRACT);
        activity.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activity.setRiskLevel(RopaActivityVersion.RiskLevel.MED);
        activityVersionRepository.save(activity);

        RopaDataCategory category = new RopaDataCategory();
        category.setTenantId(tenantId);
        category.setCategoryKey(RopaDataCategory.CategoryKey.PII);
        category.setLabel("PII");
        category.setSensitive(true);
        dataCategoryRepository.save(category);
        dataCategoryId = category.getDataCategoryId();
    }

    @Test
    void shouldExportCrossBorderReport() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence/artifacts"))
            .willReturn(WireMock.aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"evidence/crossborder/1\",\"artifactHash\":\"hash-cb-1\"}")));

        UUID vendorId = UUID.randomUUID();

        CrossBorderTransferUpsertRequest request = new CrossBorderTransferUpsertRequest();
        request.setSystemId(systemId);
        request.setActivityId(activityId);
        request.setVendorId(vendorId);
        request.setSourceRegion("INDIA");
        request.setDestinationRegion("US");
        request.setTransferMechanism(TransferMechanism.SCC);
        request.setLegalBasis("DPDP_S16");
        request.setFrequency(TransferFrequency.CONTINUOUS);
        request.setStartedAt(Instant.parse("2026-02-01T00:00:00Z"));
        request.setDataCategoryIds(List.of(dataCategoryId));
        request.setPurposeVersionIds(List.of(UUID.randomUUID()));

        mockMvc.perform(put("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        CrossBorderReportFilters filters = new CrossBorderReportFilters();
        filters.setVendorId(vendorId);
        filters.setDataCategoryId(dataCategoryId);

        CrossBorderReportExportRequest exportRequest = new CrossBorderReportExportRequest();
        exportRequest.setFilters(filters);

        mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-2")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.rowCount").value(1))
            .andExpect(jsonPath("$.rows.length()" ).value(1))
            .andExpect(jsonPath("$.artifactRef").value("evidence/crossborder/1"))
            .andExpect(jsonPath("$.artifactHash").value("hash-cb-1"));

        Integer reportCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.ropa_report_exports WHERE tenant_id = ? AND report_type = 'CROSS_BORDER_REPORT' AND status = 'CREATED' AND idempotency_key = 'EXP-2' AND artifact_ref IS NOT NULL AND artifact_hash IS NOT NULL AND payload_hash IS NOT NULL",
            Integer.class,
            tenantId
        );
        assertThat(reportCount).isNotNull();
        assertThat(reportCount).isGreaterThanOrEqualTo(1);

        Integer auditRequested = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'CROSS_BORDER_REPORT_EXPORT_REQUESTED'",
            Integer.class,
            tenantId
        );
        Integer auditCreated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'CROSS_BORDER_REPORT_EXPORT_CREATED'",
            Integer.class,
            tenantId
        );
        Integer outboxRequested = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.cross_border_report_export_requested'",
            Integer.class,
            tenantId
        );
        Integer outboxCreated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.cross_border_report_export_created'",
            Integer.class,
            tenantId
        );
        Integer evidenceAudit = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'ROPA_EVIDENCE_ARTIFACT_STORED'",
            Integer.class,
            tenantId
        );
        Integer evidenceOutbox = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.ropa_evidence_artifact_stored'",
            Integer.class,
            tenantId
        );
        assertThat(auditRequested).isNotNull();
        assertThat(auditCreated).isNotNull();
        assertThat(outboxRequested).isNotNull();
        assertThat(outboxCreated).isNotNull();
        assertThat(evidenceAudit).isNotNull();
        assertThat(evidenceOutbox).isNotNull();
        assertThat(auditRequested).isGreaterThanOrEqualTo(1);
        assertThat(auditCreated).isGreaterThanOrEqualTo(1);
        assertThat(outboxRequested).isGreaterThanOrEqualTo(1);
        assertThat(outboxCreated).isGreaterThanOrEqualTo(1);
        assertThat(evidenceAudit).isGreaterThanOrEqualTo(1);
        assertThat(evidenceOutbox).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnSameExportOnIdempotentReplay() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence/artifacts"))
            .willReturn(WireMock.aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"evidence/crossborder/2\",\"artifactHash\":\"hash-cb-2\"}")));

        UUID vendorId = UUID.randomUUID();
        createTransfer(vendorId);

        CrossBorderReportFilters filters = new CrossBorderReportFilters();
        filters.setVendorId(vendorId);
        filters.setDataCategoryId(dataCategoryId);

        CrossBorderReportExportRequest exportRequest = new CrossBorderReportExportRequest();
        exportRequest.setFilters(filters);

        String response1 = mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-3")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String response2 = mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-3")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        var json1 = objectMapper.readTree(response1);
        var json2 = objectMapper.readTree(response2);
        assertThat(json1.get("reportExportId").asText()).isEqualTo(json2.get("reportExportId").asText());
        assertThat(json1.get("status").asText()).isEqualTo(json2.get("status").asText());
        assertThat(json1.get("artifactRef").asText()).isEqualTo(json2.get("artifactRef").asText());
        assertThat(json1.get("artifactHash").asText()).isEqualTo(json2.get("artifactHash").asText());
        assertThat(json1.get("rows")).isEqualTo(json2.get("rows"));
        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/evidence/artifacts")));
    }

    @Test
    void shouldFailWhenEvidenceServiceUnavailable() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence/artifacts"))
            .willReturn(WireMock.aResponse().withStatus(500)));

        UUID vendorId = UUID.randomUUID();
        createTransfer(vendorId);

        CrossBorderReportFilters filters = new CrossBorderReportFilters();
        filters.setVendorId(vendorId);
        filters.setDataCategoryId(dataCategoryId);

        CrossBorderReportExportRequest exportRequest = new CrossBorderReportExportRequest();
        exportRequest.setFilters(filters);

        mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-4")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void testReplayDoesNotEmitDuplicateEventsOrCallEvidenceTwice() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence/artifacts"))
            .willReturn(WireMock.aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"evidence/crossborder/replay\",\"artifactHash\":\"hash-cb-replay\"}")));

        UUID vendorId = UUID.randomUUID();
        createTransfer(vendorId);

        CrossBorderReportFilters filters = new CrossBorderReportFilters();
        filters.setVendorId(vendorId);
        filters.setDataCategoryId(dataCategoryId);

        CrossBorderReportExportRequest exportRequest = new CrossBorderReportExportRequest();
        exportRequest.setFilters(filters);

        int auditCreatedBefore = countAuditEvents("CROSS_BORDER_REPORT_EXPORT_CREATED");
        int auditEvidenceBefore = countAuditEvents("ROPA_EVIDENCE_ARTIFACT_STORED");
        int outboxCreatedBefore = countOutboxEvents("ropa.cross_border_report_export_created");
        int outboxEvidenceBefore = countOutboxEvents("ropa.ropa_evidence_artifact_stored");

        String response1 = mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "K1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andReturn().getResponse().getContentAsString();

        String response2 = mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "K1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andReturn().getResponse().getContentAsString();

        var json1 = objectMapper.readTree(response1);
        var json2 = objectMapper.readTree(response2);
        assertThat(json1.get("reportExportId").asText()).isEqualTo(json2.get("reportExportId").asText());
        assertThat(json1.get("artifactRef").asText()).isEqualTo(json2.get("artifactRef").asText());
        assertThat(json1.get("artifactHash").asText()).isEqualTo(json2.get("artifactHash").asText());

        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/evidence/artifacts")));

        int auditCreatedAfter = countAuditEvents("CROSS_BORDER_REPORT_EXPORT_CREATED");
        int auditEvidenceAfter = countAuditEvents("ROPA_EVIDENCE_ARTIFACT_STORED");
        int outboxCreatedAfter = countOutboxEvents("ropa.cross_border_report_export_created");
        int outboxEvidenceAfter = countOutboxEvents("ropa.ropa_evidence_artifact_stored");

        assertThat(auditCreatedAfter).isEqualTo(auditCreatedBefore + 1);
        assertThat(auditEvidenceAfter).isEqualTo(auditEvidenceBefore + 1);
        assertThat(outboxCreatedAfter).isEqualTo(outboxCreatedBefore + 1);
        assertThat(outboxEvidenceAfter).isEqualTo(outboxEvidenceBefore + 1);

        Map<String, Object> exportRow = jdbcTemplate.queryForMap(
            "SELECT status, artifact_ref, artifact_hash FROM ropa.ropa_report_exports WHERE tenant_id = ? AND idempotency_key = 'K1' AND report_type = 'CROSS_BORDER_REPORT'",
            tenantId
        );
        assertThat(exportRow.get("artifact_ref")).isEqualTo("evidence/crossborder/replay");
        assertThat(exportRow.get("artifact_hash")).isEqualTo("hash-cb-replay");
    }

    @Test
    void testFailureDoesNotStoreArtifactAndDoesNotEmitEvidenceStoredEvent() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence/artifacts"))
            .willReturn(WireMock.aResponse().withStatus(500)));

        UUID vendorId = UUID.randomUUID();
        createTransfer(vendorId);

        CrossBorderReportFilters filters = new CrossBorderReportFilters();
        filters.setVendorId(vendorId);
        filters.setDataCategoryId(dataCategoryId);

        CrossBorderReportExportRequest exportRequest = new CrossBorderReportExportRequest();
        exportRequest.setFilters(filters);

        int evidenceAuditBefore = countAuditEvents("ROPA_EVIDENCE_ARTIFACT_STORED");
        int evidenceOutboxBefore = countOutboxEvents("ropa.ropa_evidence_artifact_stored");

        mockMvc.perform(post("/cross-border/exports/report")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "KFAIL")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exportRequest)))
            .andExpect(status().isServiceUnavailable());

        Map<String, Object> exportRow = jdbcTemplate.queryForMap(
            "SELECT status, artifact_ref, artifact_hash, error_code, error_message FROM ropa.ropa_report_exports WHERE tenant_id = ? AND idempotency_key = 'KFAIL' AND report_type = 'CROSS_BORDER_REPORT'",
            tenantId
        );
        assertThat(exportRow.get("status")).isEqualTo("FAILED");
        assertThat(exportRow.get("artifact_ref")).isNull();
        assertThat(exportRow.get("artifact_hash")).isNull();
        assertThat(exportRow.get("error_code") != null || exportRow.get("error_message") != null).isTrue();

        int evidenceAuditAfter = countAuditEvents("ROPA_EVIDENCE_ARTIFACT_STORED");
        int evidenceOutboxAfter = countOutboxEvents("ropa.ropa_evidence_artifact_stored");
        assertThat(evidenceAuditAfter).isEqualTo(evidenceAuditBefore);
        assertThat(evidenceOutboxAfter).isEqualTo(evidenceOutboxBefore);
    }

    private int countAuditEvents(String action) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = ?",
            Integer.class,
            tenantId,
            action
        );
        return count == null ? 0 : count;
    }

    private int countOutboxEvents(String eventType) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = ?",
            Integer.class,
            tenantId,
            eventType
        );
        return count == null ? 0 : count;
    }

    private void createTransfer(UUID vendorId) throws Exception {
        CrossBorderTransferUpsertRequest request = new CrossBorderTransferUpsertRequest();
        request.setSystemId(systemId);
        request.setActivityId(activityId);
        request.setVendorId(vendorId);
        request.setSourceRegion("INDIA");
        request.setDestinationRegion("US");
        request.setTransferMechanism(TransferMechanism.SCC);
        request.setLegalBasis("DPDP_S16");
        request.setFrequency(TransferFrequency.CONTINUOUS);
        request.setStartedAt(Instant.parse("2026-02-01T00:00:00Z"));
        request.setDataCategoryIds(List.of(dataCategoryId));
        request.setPurposeVersionIds(List.of(UUID.randomUUID()));

        mockMvc.perform(put("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "EXP-TR-" + vendorId)
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
