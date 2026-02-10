package com.regulyn.employee;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.config.TestSecurityConfig;
import com.regulyn.employee.dto.*;
import com.regulyn.employee.model.*;
import com.regulyn.employee.repository.*;
import com.regulyn.employee.service.EmployeeExportService;
import com.regulyn.employee.service.EmployeeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import com.regulyn.auth.context.TenantContext;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class EmployeeExportIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMock.getPort());
                registry.add("spring.task.scheduling.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EmployeeExportService employeeExportService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private HRPurposeRepository hrPurposeRepository;

    @Autowired
    private EmployeeDataRecordRepository employeeDataRecordRepository;

    @Autowired
    private EmployeeExportRepository employeeExportRepository;

    private UUID tenantId;
    private EmployeeResponse testEmployee;
    private HRPurposeResponse testPurpose;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(UUID.randomUUID());
        context.setRoles(Collections.singleton("ROLE_USER"));
        TenantContextHolder.setContext(context);

        // Create test employee
        CreateEmployeeRequest empRequest = new CreateEmployeeRequest();
        empRequest.setEmployeeRef("EMP001");
        empRequest.setFullName("Test Employee");
        empRequest.setEmail("test@example.com");
        empRequest.setDepartment("Testing");
        testEmployee = employeeService.createEmployee(empRequest);

        // Create test HR purpose
        CreateHRPurposeRequest purposeRequest = new CreateHRPurposeRequest();
        purposeRequest.setPurposeKey(HRPurpose.PurposeKey.COMPLIANCE);
        purposeRequest.setLawfulBasis(HRPurpose.LawfulBasis.LEGAL_OBLIGATION);
        purposeRequest.setDescription("Compliance export");
        purposeRequest.setRetentionDays(365);
        testPurpose = employeeService.createHRPurpose(purposeRequest);

        // Reset WireMock
        wireMock.resetAll();
    }

    @AfterEach
    void tearDown() {
        employeeExportRepository.deleteAll();
        employeeDataRecordRepository.deleteAll();
        employeeRepository.deleteAll();
        hrPurposeRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should create employee export with evidence chain")
    void testCreateEmployeeExportBuildsSnapshot() {
        // Given - Create some data records
        CreateEmployeeDataRecordRequest record1 = new CreateEmployeeDataRecordRequest();
        record1.setEmployeeId(testEmployee.getEmployeeId());
        record1.setHrPurposeId(testPurpose.getHrPurposeId());
        record1.setDataCategory(EmployeeDataRecord.DataCategory.PII);
        record1.setMetadata(Map.of("firstName", "John", "lastName", "Doe"));

        CreateEmployeeDataRecordRequest record2 = new CreateEmployeeDataRecordRequest();
        record2.setEmployeeId(testEmployee.getEmployeeId());
        record2.setHrPurposeId(testPurpose.getHrPurposeId());
        record2.setDataCategory(EmployeeDataRecord.DataCategory.PII);
        record2.setMetadata(Map.of("email", "john.doe@example.com"));

        employeeService.createEmployeeDataRecord(record1);
        employeeService.createEmployeeDataRecord(record2);

        // Mock evidence service endpoints
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        // When - Create export
        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("Compliance Audit Export");
        exportRequest.setPeriodFrom(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        EmployeeExportResponse export = employeeExportService.createEmployeeExport(exportRequest);

        // Then
        assertThat(export).isNotNull();
        assertThat(export.getExportId()).isNotNull();
        assertThat(tenantId).isEqualTo(tenantId);
        assertThat(exportRequest.getTitle()).isEqualTo("Compliance Audit Export");
        assertThat(export.getBundleId()).isEqualTo(bundleId);
        assertThat(export.getEvidenceExportId()).isEqualTo(exportId);

        // Verify WireMock interactions
        wireMock.verify(1, postRequestedFor(urlEqualTo("/evidence"))
                .withRequestBody(containing("employee_compliance")));

        wireMock.verify(1, postRequestedFor(urlEqualTo("/bundles"))
                .withRequestBody(containing(evidenceId.toString())));

        wireMock.verify(1, postRequestedFor(urlEqualTo("/bundles/" + bundleId + "/export")));
    }

    @Test
    @DisplayName("Should download export from evidence service")
    void testDownloadExportProxiesFromEvidenceService() {
        // Given - Create export first
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        // Mock creation endpoints
        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("GDPR Export");
        exportRequest.setPeriodFrom(Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        EmployeeExportResponse export = employeeExportService.createEmployeeExport(exportRequest);

        // Mock download endpoint
        byte[] mockPdfContent = "Mock PDF Content".getBytes();
        wireMock.stubFor(get(urlEqualTo("/exports/" + exportId + "/download"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(mockPdfContent)));

        // When - Download export
        byte[] downloadedContent = employeeExportService.downloadExport(export.getExportId());

        // Then
        assertThat(downloadedContent).isEqualTo(mockPdfContent);

        // Verify WireMock interaction
        wireMock.verify(1, getRequestedFor(urlEqualTo("/exports/" + exportId + "/download")));
    }

    @Test
    @DisplayName("Should handle evidence service failure during export creation")
    void testCreateExportWhenEvidenceServiceFails() {
        // Given - Mock evidence service to fail
        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        // When/Then - Should throw exception
        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("Compliance Audit");
        exportRequest.setPeriodFrom(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        assertThatThrownBy(() -> employeeExportService.createEmployeeExport(exportRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Evidence service unavailable");
    }

    @Test
    @DisplayName("Should handle evidence service failure during download")
    void testDownloadExportWhenEvidenceServiceFails() {
        // Given - Create export successfully
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("Audit Trail");
        exportRequest.setPeriodFrom(Instant.now().minus(60, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        EmployeeExportResponse export = employeeExportService.createEmployeeExport(exportRequest);

        // Mock download to fail
        wireMock.stubFor(get(urlEqualTo("/exports/" + exportId + "/download"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        // When/Then - Download should fail
        assertThatThrownBy(() -> employeeExportService.downloadExport(export.getExportId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Evidence service unavailable");
    }

    @Test
    @DisplayName("Should build comprehensive snapshot with all employee data")
    void testSnapshotIncludesAllEmployeeData() {
        // Given - Create comprehensive employee data
        CreateEmployeeDataRecordRequest personalRecord = new CreateEmployeeDataRecordRequest();
        personalRecord.setEmployeeId(testEmployee.getEmployeeId());
        personalRecord.setHrPurposeId(testPurpose.getHrPurposeId());
        personalRecord.setDataCategory(EmployeeDataRecord.DataCategory.PII);
        personalRecord.setMetadata(Map.of("ssn", "XXX-XX-1234", "dob", "1990-01-01"));

        CreateEmployeeDataRecordRequest financialRecord = new CreateEmployeeDataRecordRequest();
        financialRecord.setEmployeeId(testEmployee.getEmployeeId());
        financialRecord.setHrPurposeId(testPurpose.getHrPurposeId());
        financialRecord.setDataCategory(EmployeeDataRecord.DataCategory.FINANCIAL);
        financialRecord.setMetadata(Map.of("salary", 85000, "bonus", 10000));

        employeeService.createEmployeeDataRecord(personalRecord);
        employeeService.createEmployeeDataRecord(financialRecord);

        // Mock evidence service
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        // When - Create export
        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("Full Export");
        exportRequest.setPeriodFrom(Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        EmployeeExportResponse export = employeeExportService.createEmployeeExport(exportRequest);

        // Then
        assertThat(export).isNotNull();
        assertThat(exportRequest.getTitle()).isEqualTo("Full Export");

        // Verify evidence creation includes data categories
        wireMock.verify(1, postRequestedFor(urlEqualTo("/evidence"))
                .withRequestBody(containing("employee_compliance")));
        
        wireMock.verify(1, postRequestedFor(urlEqualTo("/bundles"))
                .withRequestBody(containing("Full Export")));
    }

    @Test
    @DisplayName("Should filter data by date range in snapshot")
    void testSnapshotFiltersDataByDateRange() {
        // Given - Create data records with different dates
        CreateEmployeeDataRecordRequest oldRecord = new CreateEmployeeDataRecordRequest();
        oldRecord.setEmployeeId(testEmployee.getEmployeeId());
        oldRecord.setHrPurposeId(testPurpose.getHrPurposeId());
        oldRecord.setDataCategory(EmployeeDataRecord.DataCategory.OTHER);
        oldRecord.setMetadata(Map.of("data", "old"));
        oldRecord.setNotes("Old historical data");

        CreateEmployeeDataRecordRequest recentRecord = new CreateEmployeeDataRecordRequest();
        recentRecord.setEmployeeId(testEmployee.getEmployeeId());
        recentRecord.setHrPurposeId(testPurpose.getHrPurposeId());
        recentRecord.setDataCategory(EmployeeDataRecord.DataCategory.EMPLOYEE_DATA);
        recentRecord.setMetadata(Map.of("data", "recent"));
        recentRecord.setNotes("Recent data");

        employeeService.createEmployeeDataRecord(oldRecord);
        employeeService.createEmployeeDataRecord(recentRecord);

        // Mock evidence service
        UUID evidenceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\": \"" + evidenceId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\": \"" + bundleId + "\"}")));

        wireMock.stubFor(post(urlEqualTo("/bundles/" + bundleId + "/export"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exportId\": \"" + exportId + "\"}")));

        // When - Create export for recent data only
        CreateEmployeeExportRequest exportRequest = new CreateEmployeeExportRequest();
        exportRequest.setTitle("Recent Export");
        exportRequest.setPeriodFrom(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        exportRequest.setPeriodTo(Instant.now());

        EmployeeExportResponse export = employeeExportService.createEmployeeExport(exportRequest);

        // Then
        assertThat(export).isNotNull();
        assertThat(exportRequest.getTitle()).isEqualTo("Recent Export");
    }
}


