package com.regulyn.vendor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.vendor.TestSecurityConfig;
import com.regulyn.vendor.dto.*;
import com.regulyn.vendor.model.SharingRecord;
import com.regulyn.vendor.model.Vendor;
import com.regulyn.vendor.repository.SharingRecordRepository;
import com.regulyn.vendor.repository.VendorExportRepository;
import com.regulyn.vendor.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
public class VendorExportServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_test")
        .withUsername("test")
        .withPassword("test");

    private static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "vendor");
        
        // Start WireMock
        wireMockServer = new WireMockServer(8083);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8083);
        
        registry.add("evidence.service.url", () -> "http://localhost:8083");
    }

    @Autowired
    private VendorService vendorService;

    @Autowired
    private VendorExportService vendorExportService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private SharingRecordRepository sharingRecordRepository;

    @Autowired
    private VendorExportRepository vendorExportRepository;

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
        sharingRecordRepository.deleteAll();
        vendorExportRepository.deleteAll();
        vendorRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateVendorExportAndDownload() {
        // Create vendor and sharing record
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Export Test Vendor");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("India");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.INDIA);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.LOW);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
        sharingRequest.setVendorId(vendor.getVendorId());
        sharingRequest.setSharingPurpose("Export testing");
        sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONTRACT);
        sharingRequest.setDataCategories(List.of("PII", "FINANCIAL"));
        sharingRequest.setFrequency(SharingRecord.Frequency.ONGOING);
        sharingRequest.setTransferCrossBorder(true);
        sharingRequest.setTransferToRegions(List.of("US", "EU"));

        vendorService.createSharingRecord(sharingRequest);

        // Setup WireMock stubs
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
                .withBody("{\"evidenceExportId\":\"" + evidenceExportId + "\"}")));

        byte[] exportData = "Export data content".getBytes();
        stubFor(get(urlEqualTo("/exports/" + evidenceExportId + "/download"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withBody(exportData)));

        // Create export
        CreateVendorExportRequest exportRequest = new CreateVendorExportRequest();
        VendorExportResponse exportResponse = vendorExportService.createVendorExport(exportRequest);

        assertThat(exportResponse.getExportId()).isNotNull();
        assertThat(exportResponse.getBundleId()).isNotNull();
        assertThat(exportResponse.getEvidenceExportId()).isNotNull();
        assertThat(exportResponse.getDownloadUrl()).contains("/vendor/exports/");

        // Download export
        byte[] downloadedData = vendorExportService.downloadVendorExport(exportResponse.getExportId());
        assertThat(downloadedData).isNotNull();
        assertThat(new String(downloadedData)).isEqualTo("Export data content");

        // Verify WireMock calls
        verify(1, postRequestedFor(urlEqualTo("/evidence")));
        verify(1, postRequestedFor(urlEqualTo("/bundles")));
        verify(1, postRequestedFor(urlEqualTo("/bundles/" + bundleId + "/export")));
        verify(1, getRequestedFor(urlEqualTo("/exports/" + evidenceExportId + "/download")));
    }
}
