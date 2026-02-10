package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
public class RopaExportServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_test")
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
        wireMockServer = new WireMockServer(9999);
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
    private RopaService ropaService;

    @Autowired
    private RopaExportService ropaExportService;

    @Autowired
    private RopaSystemRepository systemRepository;

    @Autowired
    private RopaDataCategoryRepository dataCategoryRepository;

    @Autowired
    private RopaActivityVersionRepository activityVersionRepository;

    @Autowired
    private RopaActivitySystemRepository activitySystemRepository;

    @Autowired
    private RopaActivityDataCategoryRepository activityDataCategoryRepository;

    @Autowired
    private RopaExportRepository exportRepository;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        context.setUserId(USER_ID);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
        wireMockServer.resetAll();
    }

    @AfterEach
    void tearDown() {
        exportRepository.deleteAll();
        activitySystemRepository.deleteAll();
        activityDataCategoryRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    void shouldExportRopaAndDownload() {
        // Create test data
        UUID systemId = createTestSystem("CRM", RopaSystem.SystemType.APP);
        UUID categoryId = createTestDataCategory(RopaDataCategory.CategoryKey.PII);
        UUID activityId = createTestActivity("Customer Processing");

        LinkActivityRequest linkRequest = new LinkActivityRequest();
        linkRequest.setSystemIds(List.of(systemId));
        linkRequest.setDataCategoryIds(List.of(categoryId));
        ropaService.linkActivity(activityId, linkRequest);
        ropaService.publishActivity(activityId);

        // Mock evidence service
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID evidenceExportId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + evidenceId + "\"}")));

        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bundleId\":\"" + bundleId + "\"}")));

        stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"exportId\":\"" + evidenceExportId + "\"}")));

        stubFor(get(urlEqualTo("/exports/" + evidenceExportId + "/download"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withBody("mock export data")));

        // Create export
        CreateRopaExportRequest exportRequest = new CreateRopaExportRequest();
        exportRequest.setTitle("Q1 2026 ROPA");
        exportRequest.setPeriodFrom(LocalDate.of(2026, 1, 1));
        exportRequest.setPeriodTo(LocalDate.of(2026, 3, 31));

        CreateRopaExportRequest.ExportFilters filters = new CreateRopaExportRequest.ExportFilters();
        filters.setStatus(RopaActivityVersion.Status.PUBLISHED);
        exportRequest.setFilters(filters);

        RopaExportResponse exportResponse = ropaExportService.createRopaExport(exportRequest);

        assertThat(exportResponse.getBundleId()).isEqualTo(bundleId);
        assertThat(exportResponse.getExportId()).isNotNull();
        assertThat(exportResponse.getDownloadPath()).contains("/ropa/exports/");

        // Verify evidence API was called
        verify(postRequestedFor(urlEqualTo("/evidence"))
            .withRequestBody(containing("ROPA_SNAPSHOT")));

        verify(postRequestedFor(urlEqualTo("/bundles"))
            .withRequestBody(containing("AUDIT_EXPORT")));

        // Download export
        byte[] downloadedData = ropaExportService.downloadRopaExport(exportResponse.getExportId());
        assertThat(downloadedData).isNotNull();
        assertThat(new String(downloadedData)).isEqualTo("mock export data");

        verify(getRequestedFor(urlEqualTo("/exports/" + evidenceExportId + "/download")));
    }

    // Helper methods
    private UUID createTestSystem(String name, RopaSystem.SystemType type) {
        CreateSystemRequest request = new CreateSystemRequest();
        request.setSystemName(name);
        request.setSystemType(type);
        request.setLocation(RopaSystem.Location.INDIA);
        request.setCriticality(RopaSystem.Criticality.MED);
        return ropaService.createSystem(request).getSystemId();
    }

    private UUID createTestDataCategory(RopaDataCategory.CategoryKey key) {
        CreateDataCategoryRequest request = new CreateDataCategoryRequest();
        request.setCategoryKey(key);
        request.setLabel(key.name());
        request.setSensitive(true);
        return ropaService.createDataCategory(request).getDataCategoryId();
    }

    private UUID createTestActivity(String name) {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setActivityName(name);
        request.setPurpose("Test purpose");
        request.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONSENT);
        request.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        request.setRiskLevel(RopaActivityVersion.RiskLevel.LOW);
        return ropaService.createActivity(request).getActivityId();
    }
}
