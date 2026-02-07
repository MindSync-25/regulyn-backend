package com.regulyn.notification;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.integration.EvidenceClient;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import com.regulyn.notification.service.NotificationRetryWorkerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class NotificationRound2Part4Test {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("notification_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "notification");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "notification");
        registry.add("audit.schema", () -> "notification");
        registry.add("notification.retry.enabled", () -> "false");
        registry.add("consent.enabled", () -> "false");
    }

    @Autowired
    private NotificationRetryWorkerService retryWorkerService;

    @Autowired
    private NotificationMessageRepository messageRepository;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private NotificationTemplateVersionRepository versionRepository;

    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private NotificationProvider notificationProvider;

    @MockBean
    private EvidenceClient evidenceClient;

    @MockBean
    private ApiKeyValidator apiKeyValidator;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        when(notificationProvider.getChannel()).thenReturn("EMAIL");
        when(notificationProvider.getProviderName()).thenReturn("TEST");
        when(apiKeyValidator.validate(anyString()))
            .thenReturn(ApiKeyValidator.ApiKeyValidationResult.valid(UUID.randomUUID(), "test"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM notification.outbox_events");
        jdbcTemplate.update("DELETE FROM notification.audit_events");
        jdbcTemplate.update("DELETE FROM notification.notification_messages");
        jdbcTemplate.update("DELETE FROM notification.notification_requests");
        templateRepository.findAll().forEach(t -> {
            t.setActiveVersionId(null);
            templateRepository.save(t);
        });
        languageRepository.deleteAll();
        versionRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void retry_worker_picks_due_failed_retryable_and_sends_and_marks_sent() {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids, Map.of("user_name", "Jane"));
        NotificationMessage message = createMessage(request, ids, "FAILED_RETRYABLE", 0, Instant.now().minusSeconds(5));

        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.success("TEST", "provider-1"));
        when(evidenceClient.createNotificationSentArtifact(any(), anyString()))
            .thenReturn("evidence-sent");

        retryWorkerService.runRetryBatch();

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getNextAttemptAt()).isNull();

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_SENT'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_SENT'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void retry_worker_applies_backoff_and_marks_failed_retryable_when_retryable_failure() {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids, Map.of("user_name", "Jane"));
        NotificationMessage message = createMessage(request, ids, "FAILED_RETRYABLE", 0, Instant.now().minusSeconds(5));

        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.failure("TEST", "temporary", true));

        Instant start = Instant.now();
        retryWorkerService.runRetryBatch();

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FAILED_RETRYABLE");
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getNextAttemptAt()).isNotNull();
        Duration backoff = Duration.between(start, updated.getNextAttemptAt());
        assertThat(backoff.getSeconds()).isBetween(30L, 120L);

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_FAILED_RETRYABLE'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_FAILED_RETRYABLE'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void retry_worker_marks_failed_terminal_after_max_attempts() {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids, Map.of("user_name", "Jane"));
        NotificationMessage message = createMessage(request, ids, "FAILED_RETRYABLE", 2, Instant.now().minusSeconds(5));
        message.setMaxAttempts(3);
        messageRepository.save(message);

        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.failure("TEST", "permanent", true));
        when(evidenceClient.createNotificationFailedArtifact(any(), anyString()))
            .thenReturn("evidence-failed");

        retryWorkerService.runRetryBatch();

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FAILED_TERMINAL");
        assertThat(updated.getAttemptCount()).isEqualTo(3);
        assertThat(updated.getNextAttemptAt()).isNull();

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_FAILED_TERMINAL'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_FAILED_TERMINAL'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
        verify(evidenceClient).createNotificationFailedArtifact(any(), anyString());
    }

    @Test
    void concurrency_safety_skip_locked_claims_once() throws Exception {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids, Map.of("user_name", "Jane"));
        List<NotificationMessage> messages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            messages.add(createMessage(request, ids, "FAILED_RETRYABLE", 0, Instant.now().minusSeconds(5), "dp-001+" + i + "@example.com"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        CompletableFuture<List<UUID>> first = CompletableFuture.supplyAsync(() -> {
            await(startLatch);
            return retryWorkerService.claimDueMessages(3).stream().map(NotificationMessage::getId).toList();
        }, executor);
        CompletableFuture<List<UUID>> second = CompletableFuture.supplyAsync(() -> {
            await(startLatch);
            return retryWorkerService.claimDueMessages(3).stream().map(NotificationMessage::getId).toList();
        }, executor);

        startLatch.countDown();
        List<UUID> firstIds = first.get();
        List<UUID> secondIds = second.get();
        executor.shutdown();

        Set<UUID> union = new HashSet<>(firstIds);
        union.addAll(secondIds);
        Set<UUID> intersection = new HashSet<>(firstIds);
        intersection.retainAll(secondIds);

        assertThat(union).hasSize(6);
        assertThat(intersection).isEmpty();

        List<NotificationMessage> updated = messageRepository.findAll();
        assertThat(updated).hasSize(6);
        assertThat(updated).allMatch(m -> "RETRY_IN_PROGRESS".equals(m.getStatus()));
    }

    private TemplateIds createTemplateAndVersion(String templateKey, String category) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId(TENANT_ID.toString());
        template.setTemplateKey(templateKey);
        template.setCategory(category);
        template.setDefaultLanguage("en");
        template.setTitle("Test Template");
        template.setEnabled(true);
        template.setCreatedBy("system");
        template = templateRepository.save(template);

        NotificationTemplateVersion version = new NotificationTemplateVersion();
        version.setTenantId(TENANT_ID.toString());
        version.setTemplateId(template.getTemplateId());
        version.setVersionNumber(1);
        version.setStatus("PUBLISHED");
        version.setChangeSummary("test");
        version.setPublishedAt(Instant.now());
        version.setCreatedBy("system");
        version = versionRepository.save(version);

        template.setActiveVersionId(version.getVersionId());
        templateRepository.save(template);

        NotificationTemplateLanguage language = new NotificationTemplateLanguage();
        language.setTenantId(TENANT_ID.toString());
        language.setVersionId(version.getVersionId());
        language.setLanguage("en");
        language.setSubject("Hello {{user_name}}");
        language.setBody("Body for {{user_name}}");
        language.setFormat("TEXT");
        language.setContentHash(computeContentHash(language.getSubject(), language.getBody()));
        language.setCreatedBy("system");
        languageRepository.save(language);

        return new TemplateIds(template.getTemplateId(), version.getVersionId(), templateKey, category);
    }

    private NotificationRequest createRequest(TemplateIds ids, Map<String, String> variables) {
        NotificationRequest request = new NotificationRequest();
        request.setTenantId(TENANT_ID.toString());
        request.setRequestRef("ref-" + UUID.randomUUID());
        request.setTemplateId(ids.templateId());
        request.setVersionId(ids.versionId());
        request.setLanguage("en");
        request.setChannel("EMAIL");
        request.setAudienceType("DATA_PRINCIPAL");
        request.setAudienceDataPrincipalId("dp-001");
        request.setAudienceUserIds(null);
        request.setVariables(variables);
        request.setTotalRecipients(1);
        request.setCreatedBy("system");
        return requestRepository.save(request);
    }

    private NotificationMessage createMessage(
        NotificationRequest request,
        TemplateIds ids,
        String status,
        int attemptCount,
        Instant nextAttemptAt
    ) {
        return createMessage(request, ids, status, attemptCount, nextAttemptAt, "dp-001@example.com");
    }

    private NotificationMessage createMessage(
        NotificationRequest request,
        TemplateIds ids,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String recipient
    ) {
        String hashInput = request.getRequestId() + "|" + recipient + "|" + ids.templateId() + "|" + ids.category();
        byte[] hashBytes = sha256Bytes(hashInput);
        String hashHex = toHex(hashBytes);

        NotificationMessage message = new NotificationMessage();
        message.setTenantId(TENANT_ID);
        message.setNotificationRequestId(request.getRequestId());
        message.setRecipient(recipient);
        message.setChannel("EMAIL");
        message.setCategory(ids.category());
        message.setTemplateKey(ids.templateKey());
        message.setTemplateId(ids.templateId());
        message.setTemplateVersionId(ids.versionId());
        message.setLanguage("en");
        message.setMessageHash(hashBytes);
        message.setMessageHashHex(hashHex);
        message.setStatus(status);
        message.setAttemptCount(attemptCount);
        message.setMaxAttempts(3);
        message.setNextAttemptAt(nextAttemptAt);
        return messageRepository.save(message);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String computeContentHash(String subject, String body) {
        return toHex(sha256Bytes(subject + body));
    }

    private byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private record TemplateIds(UUID templateId, UUID versionId, String templateKey, String category) {}
}
