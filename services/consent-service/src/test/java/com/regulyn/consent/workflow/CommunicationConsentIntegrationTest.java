package com.regulyn.consent.workflow;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.ConsentServiceApplication;
import com.regulyn.consent.client.EvidenceClient;
import com.regulyn.consent.entity.CommunicationChannel;
import com.regulyn.consent.model.CommunicationConsentBatchRecipient;
import com.regulyn.consent.model.CommunicationConsentBatchResponse;
import com.regulyn.consent.model.CommunicationConsentRequest;
import com.regulyn.consent.model.CommunicationConsentResponse;
import com.regulyn.consent.model.CommunicationConsentStatusResponse;
import com.regulyn.consent.repository.CommunicationConsentLedgerRepository;
import com.regulyn.consent.service.CommunicationConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ConsentServiceApplication.class)
@Testcontainers
@ActiveProfiles("comm-consent-test")
public class CommunicationConsentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("consent")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CommunicationConsentService communicationConsentService;

    @Autowired
    private CommunicationConsentLedgerRepository ledgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EvidenceClient evidenceClient;

    private UUID testTenantId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        TenantContext context = new TenantContext();
        context.setTenantId(testTenantId);
        context.setUserId(testUserId);
        context.setRequestId("test-request");
        context.setTraceId("test-trace");
        TenantContextHolder.setContext(context);

        if (evidenceClient instanceof TestEvidenceClient testEvidenceClient) {
            testEvidenceClient.reset();
        }
    }

    @Test
    void optInIdempotency() {
        Instant effectiveAt = Instant.parse("2026-02-07T10:15:30Z");
        String rawText = "COMMUNICATION_CONSENT_RAW_TEXT";
        String hash = sha256(rawText);

        CommunicationConsentRequest request = new CommunicationConsentRequest(
                UUID.randomUUID(),
                effectiveAt,
                "WIDGET",
                "en",
                null,
                hash,
                "user-123",
                "user@example.com",
                "+1234567890",
                "client-ref",
                "idemp-1"
        );

        CommunicationConsentResponse first = communicationConsentService.recordOptIn(
                CommunicationChannel.EMAIL,
                request
        );
        String ledgerId = first.ledgerId().toString();
        CommunicationConsentResponse second = communicationConsentService.recordOptIn(
            CommunicationChannel.EMAIL,
            request
        );

        assertThat(first.deduped()).isFalse();
        assertThat(second.deduped()).isTrue();
        assertThat(countLedgerRows(request.dataPrincipalId(), "EMAIL", "OPTED_IN", Instant.parse("2026-02-07T10:15:00Z"))).isEqualTo(1);
        assertThat(first.evidenceArtifactId()).isNotNull();

        assertThat(countAuditByEntity("COMMUNICATION_OPT_IN", ledgerId)).isEqualTo(1);
        assertThat(countOutboxByEntity("COMMUNICATION_OPT_IN", ledgerId)).isEqualTo(1);
        assertThat(countAuditByEntity("CONSENT_LANGUAGE_SNAPSHOT_STORED", ledgerId)).isEqualTo(1);
        assertThat(countOutboxByEntity("CONSENT_LANGUAGE_SNAPSHOT_STORED", ledgerId)).isEqualTo(1);
        assertThat(countAuditByEntity("CONSENT_EVIDENCE_ARTIFACT_STORED", ledgerId)).isEqualTo(1);
        assertThat(countOutboxByEntity("CONSENT_EVIDENCE_ARTIFACT_STORED", ledgerId)).isEqualTo(1);

        assertThat(((TestEvidenceClient) evidenceClient).getCallCount()).isEqualTo(1);

        String auditPayload = jdbcTemplate.queryForObject(
            "select metadata::text from consent.audit_events where action = ? and entity_id = ? limit 1",
            String.class,
            "COMMUNICATION_OPT_IN",
            ledgerId
        );
        String outboxPayload = jdbcTemplate.queryForObject(
            "select payload::text from consent.outbox_events where event_type = ? and entity_id = ? limit 1",
            String.class,
            "COMMUNICATION_OPT_IN",
            ledgerId
        );

        assertThat(auditPayload).doesNotContain(rawText);
        assertThat(outboxPayload).doesNotContain(rawText);
        assertThat(auditPayload).doesNotContain("user@example.com");
        assertThat(outboxPayload).doesNotContain("user@example.com");
        assertThat(auditPayload).doesNotContain("+1234567890");
        assertThat(outboxPayload).doesNotContain("+1234567890");
        assertThat(auditPayload).doesNotContain("user-123");
        assertThat(outboxPayload).doesNotContain("user-123");
    }

    @Test
    void statusReflectsLatest() {
        UUID principal = UUID.randomUUID();
        String hash = sha256("OPT_IN_TEXT");

        communicationConsentService.recordOptIn(
                CommunicationChannel.SMS,
                new CommunicationConsentRequest(principal, Instant.parse("2026-02-07T10:15:00Z"), "API", null, null, hash, null, null, null, null, null)
        );
        communicationConsentService.recordOptOut(
                CommunicationChannel.SMS,
                new CommunicationConsentRequest(principal, Instant.parse("2026-02-07T10:17:00Z"), "API", null, null, hash, null, null, null, null, null)
        );

        CommunicationConsentStatusResponse status = communicationConsentService.getStatus(CommunicationChannel.SMS, principal);
        assertThat(status.state()).isEqualTo("OPTED_OUT");
        assertThat(status.ledgerId()).isNotNull();
    }

    @Test
    void batchStatusRespectsFailClosed() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        String hash = sha256("BATCH_TEXT");
        communicationConsentService.recordOptIn(
                CommunicationChannel.WHATSAPP,
                new CommunicationConsentRequest(a, Instant.parse("2026-02-07T10:15:00Z"), "ADMIN", null, null, hash, null, null, null, null, null)
        );
        communicationConsentService.recordOptOut(
                CommunicationChannel.WHATSAPP,
                new CommunicationConsentRequest(b, Instant.parse("2026-02-07T10:16:00Z"), "ADMIN", null, null, hash, null, null, null, null, null)
        );

        CommunicationConsentBatchResponse response = communicationConsentService.batchStatus(
                CommunicationChannel.WHATSAPP,
                List.of(a, b, c)
        );

        Map<UUID, String> stateById = response.results().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.regulyn.consent.model.CommunicationConsentBatchResult::dataPrincipalId,
                        com.regulyn.consent.model.CommunicationConsentBatchResult::state
                ));

        Map<UUID, Boolean> allowedById = response.results().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.regulyn.consent.model.CommunicationConsentBatchResult::dataPrincipalId,
                        com.regulyn.consent.model.CommunicationConsentBatchResult::allowed
                ));

        assertThat(stateById.get(a)).isEqualTo("OPTED_IN");
        assertThat(stateById.get(b)).isEqualTo("OPTED_OUT");
        assertThat(stateById.get(c)).isEqualTo("UNKNOWN");

        assertThat(allowedById.get(a)).isTrue();
        assertThat(allowedById.get(b)).isFalse();
        assertThat(allowedById.get(c)).isFalse();
    }

    @Test
    void readEndpointsAreSilent() {
        UUID principal = UUID.randomUUID();
        String hash = sha256("SILENT_READ_TEXT");

        communicationConsentService.recordOptIn(
                CommunicationChannel.EMAIL,
                new CommunicationConsentRequest(principal, Instant.parse("2026-02-07T10:20:00Z"), "API", null, null, hash, null, null, null, null, null)
        );

        long auditBefore = countAuditAll();
        long outboxBefore = countOutboxAll();

        communicationConsentService.getStatus(CommunicationChannel.EMAIL, principal);
        communicationConsentService.batchStatus(CommunicationChannel.EMAIL, List.of(principal, UUID.randomUUID()));

        assertThat(countAuditAll()).isEqualTo(auditBefore);
        assertThat(countOutboxAll()).isEqualTo(outboxBefore);
    }

    private long countAudit(String action) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.audit_events where action = ?",
                Long.class,
                action
        );
        return count != null ? count : 0L;
    }

    private long countAuditAll() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.audit_events",
                Long.class
        );
        return count != null ? count : 0L;
    }

    private long countOutbox(String eventType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.outbox_events where event_type = ?",
                Long.class,
                eventType
        );
        return count != null ? count : 0L;
    }

    private long countOutboxAll() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.outbox_events",
                Long.class
        );
        return count != null ? count : 0L;
    }

    private long countAuditByEntity(String action, String entityId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.audit_events where action = ? and entity_id = ?",
                Long.class,
                action,
                entityId
        );
        return count != null ? count : 0L;
    }

    private long countOutboxByEntity(String eventType, String entityId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.outbox_events where event_type = ? and entity_id = ?",
                Long.class,
                eventType,
                entityId
        );
        return count != null ? count : 0L;
    }

    private long countLedgerRows(UUID principalId, String channel, String state, Instant bucket) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.communication_consent_ledger where tenant_id = ? and data_principal_id = ? and channel = ? and state = ? and effective_time_bucket = ?",
                Long.class,
                testTenantId,
                principalId,
                channel,
                state,
                java.sql.Timestamp.from(bucket)
        );
        return count != null ? count : 0L;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @TestConfiguration
    @Profile("comm-consent-test")
    static class EvidenceClientTestConfig {
        @Bean
        @Primary
        EvidenceClient evidenceClient() {
            return new TestEvidenceClient();
        }
    }

    static class TestEvidenceClient implements EvidenceClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private final UUID fixed = UUID.fromString("11111111-1111-1111-1111-111111111111");

        @Override
        public UUID createArtifact(UUID tenantId, String artifactType, Map<String, Object> metadata) {
            callCount.incrementAndGet();
            return fixed;
        }

        int getCallCount() {
            return callCount.get();
        }

        void reset() {
            callCount.set(0);
        }
    }
}
