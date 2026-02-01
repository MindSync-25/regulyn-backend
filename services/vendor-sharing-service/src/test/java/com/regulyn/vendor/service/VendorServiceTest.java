package com.regulyn.vendor.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.vendor.TestSecurityConfig;
import com.regulyn.vendor.dto.*;
import com.regulyn.vendor.model.*;
import com.regulyn.vendor.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
public class VendorServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "vendor");
    }

    @Autowired
    private VendorService vendorService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private VendorAgreementRepository agreementRepository;

    @Autowired
    private SharingRecordRepository sharingRecordRepository;

    @Autowired
    private SharingStatusHistoryRepository statusHistoryRepository;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        context.setUserId(USER_ID);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        sharingRecordRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        agreementRepository.deleteAll();
        vendorRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateVendorAndListWithFilters() {
        // Create vendor
        CreateVendorRequest request = new CreateVendorRequest();
        request.setVendorName("AWS Inc");
        request.setVendorType(Vendor.VendorType.PROCESSOR);
        request.setContactEmail("contact@aws.com");
        request.setCountry("USA");
        request.setHostingRegion(Vendor.HostingRegion.US);
        request.setRiskLevel(Vendor.RiskLevel.LOW);
        request.setMetadata(new HashMap<>());

        VendorResponse response = vendorService.createVendor(request);

        assertThat(response.getVendorId()).isNotNull();
        assertThat(response.getVendorName()).isEqualTo("AWS Inc");
        assertThat(response.getEnabled()).isTrue();

        // List all vendors
        List<VendorResponse> vendors = vendorService.listVendors(null, null, null);
        assertThat(vendors).hasSize(1);

        // Filter by enabled
        vendors = vendorService.listVendors(true, null, null);
        assertThat(vendors).hasSize(1);

        // Filter by risk level
        vendors = vendorService.listVendors(null, Vendor.RiskLevel.LOW, null);
        assertThat(vendors).hasSize(1);

        vendors = vendorService.listVendors(null, Vendor.RiskLevel.HIGH, null);
        assertThat(vendors).isEmpty();

        // Search by name
        vendors = vendorService.listVendors(null, null, "AWS");
        assertThat(vendors).hasSize(1);

        vendors = vendorService.listVendors(null, null, "Google");
        assertThat(vendors).isEmpty();
    }

    @Test
    void shouldCreateAgreementAndListAgreements() {
        // Create vendor first
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Azure Inc");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("USA");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.US);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.MED);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        // Create agreement
        CreateAgreementRequest agreementRequest = new CreateAgreementRequest();
        agreementRequest.setAgreementType(VendorAgreement.AgreementType.DPA);
        agreementRequest.setStatus(VendorAgreement.AgreementStatus.ACTIVE);
        agreementRequest.setDocRef("DPA-2024-001");

        AgreementResponse agreement = vendorService.createAgreement(vendor.getVendorId(), agreementRequest);

        assertThat(agreement.getAgreementId()).isNotNull();
        assertThat(agreement.getVendorId()).isEqualTo(vendor.getVendorId());
        assertThat(agreement.getAgreementType()).isEqualTo(VendorAgreement.AgreementType.DPA);

        // List agreements
        List<AgreementResponse> agreements = vendorService.listAgreements(vendor.getVendorId());
        assertThat(agreements).hasSize(1);
    }

    @Test
    void shouldCreateSharingRecordSuccessfully() {
        // Create vendor
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Google Cloud");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("USA");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.US);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.LOW);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        // Create sharing record
        CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
        sharingRequest.setVendorId(vendor.getVendorId());
        sharingRequest.setSharingPurpose("Analytics processing");
        sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONTRACT);
        sharingRequest.setDataCategories(List.of("PII", "FINANCIAL"));
        sharingRequest.setFrequency(SharingRecord.Frequency.ONGOING);
        sharingRequest.setTransferCrossBorder(false);

        SharingRecordResponse sharing = vendorService.createSharingRecord(sharingRequest);

        assertThat(sharing.getSharingId()).isNotNull();
        assertThat(sharing.getVendorId()).isEqualTo(vendor.getVendorId());
        assertThat(sharing.getEnabled()).isTrue();
        assertThat(sharing.getStatus()).isEqualTo(SharingRecord.SharingStatus.ACTIVE);
    }

    @Test
    void shouldRejectCrossBorderSharingWithoutRegions() {
        // Create vendor
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Cross Border Vendor");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("USA");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.US);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.HIGH);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        // Try to create cross-border sharing without regions
        CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
        sharingRequest.setVendorId(vendor.getVendorId());
        sharingRequest.setSharingPurpose("International transfer");
        sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONSENT);
        sharingRequest.setDataCategories(List.of("PII"));
        sharingRequest.setFrequency(SharingRecord.Frequency.ONE_TIME);
        sharingRequest.setTransferCrossBorder(true);
        // Missing transferToRegions

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> vendorService.createSharingRecord(sharingRequest)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("transferToRegions is required");
    }

    @Test
    void shouldRejectSharingForDisabledVendor() {
        // Create and disable vendor
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Disabled Vendor");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("India");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.INDIA);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.LOW);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);
        vendorService.disableVendor(vendor.getVendorId());

        // Try to create sharing record
        CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
        sharingRequest.setVendorId(vendor.getVendorId());
        sharingRequest.setSharingPurpose("Test sharing");
        sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONTRACT);
        sharingRequest.setDataCategories(List.of("PII"));
        sharingRequest.setFrequency(SharingRecord.Frequency.ONGOING);
        sharingRequest.setTransferCrossBorder(false);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> vendorService.createSharingRecord(sharingRequest)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("disabled vendor");
    }

    @Test
    void shouldDisableSharingRecordAndCreateHistory() {
        // Create vendor and sharing
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Test Vendor");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("India");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.INDIA);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.LOW);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
        sharingRequest.setVendorId(vendor.getVendorId());
        sharingRequest.setSharingPurpose("Testing");
        sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONTRACT);
        sharingRequest.setDataCategories(List.of("EMPLOYEE_DATA"));
        sharingRequest.setFrequency(SharingRecord.Frequency.ONGOING);
        sharingRequest.setTransferCrossBorder(false);

        SharingRecordResponse sharing = vendorService.createSharingRecord(sharingRequest);

        // Disable sharing
        DisableSharingResponse disableResponse = vendorService.disableSharingRecord(sharing.getSharingId());

        assertThat(disableResponse.getEnabled()).isFalse();
        assertThat(disableResponse.getStatus()).isEqualTo(SharingRecord.SharingStatus.INACTIVE);

        // Verify history was created
        List<SharingStatusHistory> history = statusHistoryRepository.findByTenantIdAndSharingIdOrderByChangedAtDesc(
            TENANT_ID, sharing.getSharingId()
        );
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo("ACTIVE");
        assertThat(history.get(0).getToStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldListSharingRecordsWithPaginationAndFilters() {
        // Create vendor
        CreateVendorRequest vendorRequest = new CreateVendorRequest();
        vendorRequest.setVendorName("Filter Test Vendor");
        vendorRequest.setVendorType(Vendor.VendorType.PROCESSOR);
        vendorRequest.setCountry("India");
        vendorRequest.setHostingRegion(Vendor.HostingRegion.INDIA);
        vendorRequest.setRiskLevel(Vendor.RiskLevel.LOW);

        VendorResponse vendor = vendorService.createVendor(vendorRequest);

        // Create multiple sharing records
        for (int i = 0; i < 3; i++) {
            CreateSharingRecordRequest sharingRequest = new CreateSharingRecordRequest();
            sharingRequest.setVendorId(vendor.getVendorId());
            sharingRequest.setSharingPurpose("Purpose " + i);
            sharingRequest.setLawfulBasis(SharingRecord.LawfulBasis.CONTRACT);
            sharingRequest.setDataCategories(i == 0 ? List.of("PII") : List.of("FINANCIAL"));
            sharingRequest.setFrequency(SharingRecord.Frequency.ONGOING);
            sharingRequest.setTransferCrossBorder(i == 2);
            if (i == 2) {
                sharingRequest.setTransferToRegions(List.of("US", "EU"));
            }

            vendorService.createSharingRecord(sharingRequest);
        }

        // List all
        Page<SharingRecordResponse> page = vendorService.listSharingRecords(
            null, null, null, null, null, null, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(3);

        // Filter by vendor
        page = vendorService.listSharingRecords(
            vendor.getVendorId(), null, null, null, null, null, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(3);

        // Filter by enabled
        page = vendorService.listSharingRecords(
            null, null, null, true, null, null, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(3);

        // Filter by data category
        page = vendorService.listSharingRecords(
            null, null, null, null, "PII", null, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(1);

        // Filter by cross-border
        page = vendorService.listSharingRecords(
            null, null, null, null, null, true, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(1);

        page = vendorService.listSharingRecords(
            null, null, null, null, null, false, PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
