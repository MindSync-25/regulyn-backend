package com.regulyn.incident.workflow;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.incident.dto.*;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.entity.IncidentEscalationEntity;
import com.regulyn.incident.entity.IncidentNotification;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.repository.IncidentCaseRepository;
import com.regulyn.incident.repository.IncidentEscalationRepository;
import com.regulyn.incident.repository.IncidentNotificationRepository;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import com.regulyn.incident.service.IncidentSlaEscalationScheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfiguration.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IncidentRound2FinalE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("incident_test")
        .withUsername("test")
        .withPassword("test");

    static WireMockServer wireMock = new WireMockServer(0);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        configureFor("localhost", wireMock.port());

        stubFor(post(urlEqualTo("/send"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"notificationRequestId\":\"req-1\",\"providerMessageId\":\"msg-1\",\"status\":\"SENT\"}")));

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));

        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bundleId\":\"" + UUID.randomUUID() + "\"}")));

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "incident,outbox");

        registry.add("evidence.service.url", () -> "http://localhost:" + wireMock.port());
        registry.add("notification.service.stub", () -> "false");
        registry.add("notification.service.url", () -> "http://localhost:" + wireMock.port());
        registry.add("incident.close.requireEvidenceBundle", () -> "true");
        registry.add("incident.notifications.receiptWebhookSecret", () -> "test-secret");
        registry.add("incident.sla-escalation.dpoEmails[0]", () -> "dpo@example.com");
        registry.add("incident.sla-escalation.adminEmails[0]", () -> "admin@example.com");
    }

    @AfterAll
    static void afterAll() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IncidentCaseRepository incidentRepository;

    @Autowired
    private IncidentEscalationRepository escalationRepository;

    @Autowired
    private IncidentSlaEscalationScheduler escalationScheduler;

    @Autowired
    private IncidentNotificationRepository notificationRepository;

    @Autowired
    private NoticeDispatchLogRepository dispatchLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID testTenantId = UUID.randomUUID();
    private final UUID testActorId = UUID.randomUUID();

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", testTenantId.toString());
        headers.set("X-Actor-ID", testActorId.toString());
        headers.set("X-Actor-Role", "TENANT_ADMIN");
        return headers;
    }

    private HttpHeaders createHeaders(UUID actorId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", testTenantId.toString());
        headers.set("X-Actor-ID", actorId.toString());
        headers.set("X-Actor-Role", "TENANT_ADMIN");
        return headers;
    }

    @Test
    void escalationIdempotency_shouldInsertOnceAndEmitOnce() {
        IncidentCase incident = new IncidentCase();
        incident.setTenantId(testTenantId);
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPENED");
        incident.setOpenedAt(Instant.now().minusSeconds(49 * 3600L));
        incident.setNotifyDueAt(Instant.now().plusSeconds(10 * 3600L));
        incident.setMetadata("{}");
        incident = incidentRepository.save(incident);

        escalationScheduler.checkSlaEscalations();
        escalationScheduler.checkSlaEscalations();

        Optional<IncidentEscalationEntity> escalation =
            escalationRepository.findByIncidentIdAndThresholdHours(incident.getId(), 48);
        assertThat(escalation).isPresent();

        Long escalationCount = jdbcTemplate.queryForObject(
            "select count(*) from incident.incident_escalations where incident_id = ? and threshold_hours = 48",
            Long.class,
            incident.getId()
        );
        assertThat(escalationCount).isEqualTo(1L);

        Long outboxCount = jdbcTemplate.queryForObject(
            "select count(*) from incident.outbox_events where event_type = 'INCIDENT_SLA_THRESHOLD_REACHED'",
            Long.class
        );
        assertThat(outboxCount).isEqualTo(1L);

        Long auditCount = jdbcTemplate.queryForObject(
            "select count(*) from incident.audit_events where action = 'INCIDENT_SLA_THRESHOLD_REACHED'",
            Long.class
        );
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    void endToEndRound2Flow_shouldEmitEvidenceBundleAndUpdateReceipts() {
        CreateIncidentRequest createRequest = new CreateIncidentRequest("HIGH", "End-to-end incident", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        DraftNotificationRequest draftRequest = new DraftNotificationRequest(
            "EMAIL",
            "Draft notice",
            Map.of("recipients", List.of("user@example.com"), "subject", "Test subject")
        );
        ResponseEntity<DraftNotificationResponse> draftResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/draft",
            HttpMethod.POST,
            new HttpEntity<>(draftRequest, createHeaders()),
            DraftNotificationResponse.class
        );
        UUID notificationId = draftResponse.getBody().notificationId();

        ApproveNotificationRequest approveRequest = new ApproveNotificationRequest("approve");
        UUID approverId = UUID.randomUUID();
        restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/approve",
            HttpMethod.POST,
            new HttpEntity<>(approveRequest, createHeaders(approverId)),
            ApproveNotificationResponse.class
        );

        ResponseEntity<SendNotificationResponse> sendResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/send",
            HttpMethod.POST,
            new HttpEntity<>(null, createHeaders()),
            SendNotificationResponse.class
        );
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        IncidentNotification notification = notificationRepository
            .findByTenantIdAndNotificationId(testTenantId, notificationId)
            .orElseThrow();
        UUID draftId = notification.getRound2DraftId();

        List<NoticeDispatchLogEntity> logs = dispatchLogRepository.findByDraftId(draftId);
        assertThat(logs).isNotEmpty();
        NoticeDispatchLogEntity log = logs.get(0);

        NoticeReceiptRequest receiptRequest = new NoticeReceiptRequest(
            testTenantId,
            log.getNotificationRequestId(),
            log.getProviderMessageId(),
            "DELIVERED",
            Instant.now(),
            "receipt-ref"
        );
        HttpHeaders receiptHeaders = createHeaders();
        receiptHeaders.set("X-Webhook-Secret", "test-secret");

        ResponseEntity<Map> receiptResponse = restTemplate.exchange(
            "/incidents/notifications/receipts",
            HttpMethod.POST,
            new HttpEntity<>(receiptRequest, receiptHeaders),
            Map.class
        );
        assertThat(receiptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        NoticeDispatchLogEntity updatedLog = dispatchLogRepository.findById(log.getId()).orElseThrow();
        assertThat(updatedLog.getStatus()).isEqualTo("DELIVERED");

        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("TRIAGED", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("INVESTIGATING", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("CONTAINED", null), createHeaders()),
            TransitionResponse.class
        );

        CloseIncidentRequest closeRequest = new CloseIncidentRequest("closed", List.of());
        ResponseEntity<CloseIncidentResponse> closeResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/close",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, createHeaders()),
            CloseIncidentResponse.class
        );
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody().evidenceBundleId()).isNotNull();

        verify(postRequestedFor(urlEqualTo("/evidence")));
        verify(postRequestedFor(urlEqualTo("/bundles")));

        Long outboxCount = jdbcTemplate.queryForObject(
            "select count(*) from incident.outbox_events where event_type = 'INCIDENT_EVIDENCE_BUNDLE_CREATED'",
            Long.class
        );
        assertThat(outboxCount).isEqualTo(1L);
    }

    @Test
    void closeShouldFailWhenDispatchMissingAndEvidenceRequired() {
        CreateIncidentRequest createRequest = new CreateIncidentRequest("HIGH", "Missing dispatch", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        DraftNotificationRequest draftRequest = new DraftNotificationRequest(
            "EMAIL",
            "Draft notice",
            Map.of("recipients", List.of("user@example.com"))
        );
        ResponseEntity<DraftNotificationResponse> draftResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/draft",
            HttpMethod.POST,
            new HttpEntity<>(draftRequest, createHeaders()),
            DraftNotificationResponse.class
        );
        UUID notificationId = draftResponse.getBody().notificationId();

        ApproveNotificationRequest approveRequest = new ApproveNotificationRequest("approve");
        UUID approverId = UUID.randomUUID();
        restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/approve",
            HttpMethod.POST,
            new HttpEntity<>(approveRequest, createHeaders(approverId)),
            ApproveNotificationResponse.class
        );

        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("TRIAGED", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("INVESTIGATING", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("CONTAINED", null), createHeaders()),
            TransitionResponse.class
        );

        CloseIncidentRequest closeRequest = new CloseIncidentRequest("closed", List.of());
        ResponseEntity<String> closeResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/close",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, createHeaders()),
            String.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void closeShouldFailWhenDraftPendingAndEvidenceRequired() {
        CreateIncidentRequest createRequest = new CreateIncidentRequest("HIGH", "Pending draft", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        DraftNotificationRequest draftRequest = new DraftNotificationRequest(
            "EMAIL",
            "Draft notice",
            Map.of("recipients", List.of("user@example.com"))
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/draft",
            HttpMethod.POST,
            new HttpEntity<>(draftRequest, createHeaders()),
            DraftNotificationResponse.class
        );

        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("TRIAGED", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("INVESTIGATING", null), createHeaders()),
            TransitionResponse.class
        );
        restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(new TransitionRequest("CONTAINED", null), createHeaders()),
            TransitionResponse.class
        );

        CloseIncidentRequest closeRequest = new CloseIncidentRequest("closed", List.of());
        ResponseEntity<String> closeResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/close",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, createHeaders()),
            String.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
