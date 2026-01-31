package com.regulyn.evidence.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import com.regulyn.evidence.entity.EvidenceBundle;
import com.regulyn.evidence.entity.EvidenceExport;
import com.regulyn.evidence.entity.EvidenceRecord;
import com.regulyn.evidence.model.*;
import com.regulyn.evidence.repository.EvidenceBundleRepository;
import com.regulyn.evidence.repository.EvidenceExportRepository;
import com.regulyn.evidence.repository.EvidenceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class EvidenceBundleServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("regulyn")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EvidenceBundleService bundleService;

    @Autowired
    private EvidenceBundleRepository bundleRepository;

    @Autowired
    private EvidenceExportRepository exportRepository;

    @Autowired
    private EvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(userId);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
    }

    @Test
    void testCreateBundle() {
        // Create test evidence record
        EvidenceRecord evidence = new EvidenceRecord();
        evidence.setTenantId(tenantId);
        evidence.setEvidenceId(UUID.randomUUID().toString());
        evidence.setEvidenceType("SCREENSHOT");
        evidence.setEvidenceHash("abc123hash");
        evidence.setMetadata("{}");
        evidence.setCreatedBy(userId);
        evidence.setCreatedAt(Instant.now());
        evidenceRecordRepository.save(evidence);

        // Create bundle
        CreateBundleRequest request = new CreateBundleRequest(
                "DSAR",
                "DSAR",
                "dsar-123",
                "Test Bundle",
                "Test bundle for DSAR request",
                List.of(evidence.getEvidenceId()),
                null,
                Map.of("requestDate", "2024-01-15")
        );

        CreateBundleResponse response = bundleService.createBundle(request);

        // Assertions
        assertThat(response.bundleId()).isNotNull();
        assertThat(response.bundleHash()).isNotEmpty();
        assertThat(response.status()).isEqualTo("CREATED");

        // Verify bundle in database
        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(response.bundleId(), tenantId)
                .orElseThrow();
        assertThat(bundle.getBundleType()).isEqualTo("DSAR");
        assertThat(bundle.getReferenceId()).isEqualTo("dsar-123");

        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isNotEmpty();
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("evidence.bundle_created");
    }

    @Test
    void testExportBundle() throws Exception {
        // Create test evidence
        EvidenceRecord evidence = new EvidenceRecord();
        evidence.setTenantId(tenantId);
        evidence.setEvidenceId(UUID.randomUUID().toString());
        evidence.setEvidenceType("DOCUMENT");
        evidence.setEvidenceHash("doc123hash");
        evidence.setMetadata("{}");
        evidence.setCreatedBy(userId);
        evidence.setCreatedAt(Instant.now());
        evidenceRecordRepository.save(evidence);

        // Create bundle
        CreateBundleRequest request = new CreateBundleRequest(
                "DELETION",
                "DELETION",
                "del-456",
                "Deletion Bundle",
                "Evidence bundle for deletion request",
                List.of(evidence.getEvidenceId()),
                null,
                null
        );
        CreateBundleResponse bundleResponse = bundleService.createBundle(request);

        // Export bundle
        ExportResponse exportResponse = bundleService.exportBundle(bundleResponse.bundleId());

        // Assertions
        assertThat(exportResponse.exportId()).isNotNull();
        assertThat(exportResponse.status()).isEqualTo("READY");
        assertThat(exportResponse.exportHash()).isNotEmpty();
        assertThat(exportResponse.downloadPath()).contains(tenantId.toString());

        // Verify export in database
        EvidenceExport export = exportRepository.findByExportIdAndTenantId(exportResponse.exportId(), tenantId)
                .orElseThrow();
        assertThat(export.getStatus()).isEqualTo("READY");
        assertThat(export.getBundleId()).isEqualTo(bundleResponse.bundleId());

        // Verify bundle status updated
        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(bundleResponse.bundleId(), tenantId)
                .orElseThrow();
        assertThat(bundle.getStatus()).isEqualTo("EXPORTED");
    }

    @Test
    void testVerifyBundle_Valid() {
        // Create test evidence
        EvidenceRecord evidence = new EvidenceRecord();
        evidence.setTenantId(tenantId);
        evidence.setEvidenceId(UUID.randomUUID().toString());
        evidence.setEvidenceType("AUDIT_LOG");
        evidence.setEvidenceHash("audit789hash");
        evidence.setMetadata("{}");
        evidence.setCreatedBy(userId);
        evidence.setCreatedAt(Instant.now());
        evidenceRecordRepository.save(evidence);

        // Create bundle
        CreateBundleRequest request = new CreateBundleRequest(
                "INCIDENT",
                "INCIDENT",
                "inc-789",
                "Incident Bundle",
                "Evidence for incident investigation",
                List.of(evidence.getEvidenceId()),
                null,
                null
        );
        CreateBundleResponse bundleResponse = bundleService.createBundle(request);

        // Verify bundle
        VerifyResponse verifyResponse = bundleService.verifyBundle(bundleResponse.bundleId());

        // Assertions
        assertThat(verifyResponse.bundleId()).isEqualTo(bundleResponse.bundleId());
        assertThat(verifyResponse.valid()).isTrue();
        assertThat(verifyResponse.problems()).isEmpty();
    }

    @Test
    void testVerifyBundle_TamperedHash() {
        // Create test evidence
        EvidenceRecord evidence = new EvidenceRecord();
        evidence.setTenantId(tenantId);
        evidence.setEvidenceId(UUID.randomUUID().toString());
        evidence.setEvidenceType("EMAIL");
        evidence.setEvidenceHash("email999hash");
        evidence.setMetadata("{}");
        evidence.setCreatedBy(userId);
        evidence.setCreatedAt(Instant.now());
        evidenceRecordRepository.save(evidence);

        // Create bundle
        CreateBundleRequest request = new CreateBundleRequest(
                "AUDIT_EXPORT",
                "PERIOD",
                "2024-01",
                "Monthly Audit",
                "Audit evidence for January 2024",
                List.of(evidence.getEvidenceId()),
                null,
                null
        );
        CreateBundleResponse bundleResponse = bundleService.createBundle(request);

        // Tamper with bundle hash in database
        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(bundleResponse.bundleId(), tenantId)
                .orElseThrow();
        bundle.setBundleHash("tampered_hash_12345");
        bundleRepository.save(bundle);

        // Verify bundle
        VerifyResponse verifyResponse = bundleService.verifyBundle(bundleResponse.bundleId());

        // Assertions
        assertThat(verifyResponse.valid()).isFalse();
        assertThat(verifyResponse.problems()).isNotEmpty();
        assertThat(verifyResponse.problems().get(0)).contains("Bundle hash mismatch");
    }
}
