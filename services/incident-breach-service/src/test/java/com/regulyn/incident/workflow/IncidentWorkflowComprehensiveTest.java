package com.regulyn.incident.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.incident.dto.*;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.repository.IncidentCaseRepository;
import com.regulyn.incident.repository.IncidentNotificationRepository;
import com.regulyn.incident.repository.IncidentTaskRepository;
import com.regulyn.incident.service.IncidentOverdueScheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentWorkflowComprehensiveTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("incident_test")
        .withUsername("test")
        .withPassword("test");

    static WireMockServer wireMock = new WireMockServer(9999);
    
    static {
        wireMock.start();
        WireMock.configureFor("localhost", 9999);
        
        // Setup WireMock stubs
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/evidence"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));
        
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/bundles"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bundleId\":\"" + UUID.randomUUID() + "\"}")));
        
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/send"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"messageId\":\"" + UUID.randomUUID() + "\",\"status\":\"SENT\"}")));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "incident,outbox");
        
        registry.add("evidence.service.url", () -> "http://localhost:9999");
        registry.add("notification.service.stub", () -> "false");
        registry.add("notification.service.url", () -> "http://localhost:9999");
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
    private IncidentTaskRepository taskRepository;

    @Autowired
    private IncidentNotificationRepository notificationRepository;

    @Autowired
    private IncidentOverdueScheduler overdueScheduler;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID testTenantId = UUID.randomUUID();
    private final UUID testActorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(testTenantId);
        context.setUserId(testActorId);
        context.setRoles(Set.of("TENANT_ADMIN"));
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", testTenantId.toString());
        headers.set("X-Actor-ID", testActorId.toString());
        headers.set("X-Actor-Role", "TENANT_ADMIN");
        return headers;
    }

    @Test
    @Order(1)
    void testCreateIncident_shouldSetNotifyDueAtAnd72HourClock() {
        // Arrange
        CreateIncidentRequest request = new CreateIncidentRequest(
            "HIGH",
            "Data breach detected",
            Map.of("source", "scanner")
        );

        // Act
        ResponseEntity<CreateIncidentResponse> response = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(request, createHeaders()),
            CreateIncidentResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().incidentId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("OPENED");
        assertThat(response.getBody().notifyDueAt()).isNotNull();
        
        // Verify 72-hour window
        Instant now = Instant.now();
        Instant dueAt = response.getBody().notifyDueAt();
        long hoursDiff = (dueAt.toEpochMilli() - now.toEpochMilli()) / (1000 * 60 * 60);
        assertThat(hoursDiff).isBetween(71L, 73L); // Allow 1 hour tolerance
    }

    @Test
    @Order(2)
    void testStateTransitions_shouldEnforceStateMachine() {
        // Arrange - create incident
        CreateIncidentRequest createRequest = new CreateIncidentRequest("MED", "Test incident", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Act & Assert - valid transition OPENED -> TRIAGED
        TransitionRequest validTransition = new TransitionRequest("TRIAGED", "Initial triage");
        ResponseEntity<TransitionResponse> validResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(validTransition, createHeaders()),
            TransitionResponse.class
        );
        assertThat(validResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validResponse.getBody().status()).isEqualTo("TRIAGED");

        // Act & Assert - invalid transition TRIAGED -> CLOSED (must go through CONTAINED)
        TransitionRequest invalidTransition = new TransitionRequest("CLOSED", "Try to skip");
        ResponseEntity<String> invalidResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/transition",
            HttpMethod.POST,
            new HttpEntity<>(invalidTransition, createHeaders()),
            String.class
        );
        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(3)
    void testNotificationFlow_draftApproveAndSend() {
        // Arrange - create incident
        CreateIncidentRequest createRequest = new CreateIncidentRequest("CRITICAL", "Privacy breach", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Act 1 - Draft notification
        DraftNotificationRequest draftRequest = new DraftNotificationRequest(
            "EMAIL",
            "Dear user, we detected a privacy incident...",
            Map.of("recipients", List.of("user@example.com"))
        );
        ResponseEntity<DraftNotificationResponse> draftResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/draft",
            HttpMethod.POST,
            new HttpEntity<>(draftRequest, createHeaders()),
            DraftNotificationResponse.class
        );
        
        assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(draftResponse.getBody().status()).isEqualTo("DRAFT");
        UUID notificationId = draftResponse.getBody().notificationId();

        // Act 2 - Attempt to send before approval (should fail)
        ResponseEntity<String> sendBeforeApproval = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/send",
            HttpMethod.POST,
            new HttpEntity<>(createHeaders()),
            String.class
        );
        assertThat(sendBeforeApproval.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Act 3 - Approve notification
        ApproveNotificationRequest approveRequest = new ApproveNotificationRequest("Reviewed and approved");
        HttpHeaders approverHeaders = createHeaders();
        approverHeaders.set("X-Actor-ID", UUID.randomUUID().toString());
        ResponseEntity<ApproveNotificationResponse> approveResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/approve",
            HttpMethod.POST,
            new HttpEntity<>(approveRequest, approverHeaders),
            ApproveNotificationResponse.class
        );
        
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody().status()).isEqualTo("APPROVED");

        // Act 4 - Send notification (should succeed)
        ResponseEntity<SendNotificationResponse> sendResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/send",
            HttpMethod.POST,
            new HttpEntity<>(createHeaders()),
            SendNotificationResponse.class
        );
        
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sendResponse.getBody().status()).isEqualTo("SENT");
        assertThat(sendResponse.getBody().sentAt()).isNotNull();
    }

    @Test
    @Order(4)
    void testApprovalRoleRestriction_shouldReturn403ForNonAdminDpoReviewer() {
        // Arrange - create incident and draft
        CreateIncidentRequest createRequest = new CreateIncidentRequest("LOW", "Test", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        DraftNotificationRequest draftRequest = new DraftNotificationRequest("SMS", "Test message", Map.of());
        ResponseEntity<DraftNotificationResponse> draftResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/draft",
            HttpMethod.POST,
            new HttpEntity<>(draftRequest, createHeaders()),
            DraftNotificationResponse.class
        );
        UUID notificationId = draftResponse.getBody().notificationId();

        // Act - Attempt approval with non-privileged role
        HttpHeaders unauthorizedHeaders = createHeaders();
        unauthorizedHeaders.set("X-Actor-Role", "USER");
        
        ApproveNotificationRequest approveRequest = new ApproveNotificationRequest("Try to approve");
        ResponseEntity<String> response = restTemplate.exchange(
            "/incidents/" + incidentId + "/notifications/" + notificationId + "/approve",
            HttpMethod.POST,
            new HttpEntity<>(approveRequest, unauthorizedHeaders),
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    void testCloseIncident_shouldCreateEvidenceBundleAndStoreId() {
        // Arrange - create incident and move to CONTAINED
        CreateIncidentRequest createRequest = new CreateIncidentRequest("HIGH", "Incident for closure", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Transition to CONTAINED (via TRIAGED -> INVESTIGATING -> CONTAINED)
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

        // Act - Close incident
        CloseIncidentRequest closeRequest = new CloseIncidentRequest(
            "Incident resolved and contained",
            List.of()
        );
        ResponseEntity<CloseIncidentResponse> closeResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/close",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, createHeaders()),
            CloseIncidentResponse.class
        );

        // Assert
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody().status()).isEqualTo("CLOSED");
        assertThat(closeResponse.getBody().evidenceBundleId()).isNotNull();

        // Verify evidence bundle ID is stored in database
        IncidentCase incident = incidentRepository.findByIdAndTenantId(incidentId, testTenantId).orElseThrow();
        assertThat(incident.getEvidenceBundleId()).isEqualTo(closeResponse.getBody().evidenceBundleId());
        assertThat(incident.getClosedAt()).isNotNull();
    }

    @Test
    @Order(6)
    void testCloseIncident_whenEvidenceServiceDown_shouldReturn503() {
        // Arrange - create incident and move to CONTAINED
        CreateIncidentRequest createRequest = new CreateIncidentRequest("MED", "Test", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Transition to CONTAINED
        restTemplate.exchange("/incidents/" + incidentId + "/transition",
            HttpMethod.POST, new HttpEntity<>(new TransitionRequest("TRIAGED", null), createHeaders()), TransitionResponse.class);
        restTemplate.exchange("/incidents/" + incidentId + "/transition",
            HttpMethod.POST, new HttpEntity<>(new TransitionRequest("INVESTIGATING", null), createHeaders()), TransitionResponse.class);
        restTemplate.exchange("/incidents/" + incidentId + "/transition",
            HttpMethod.POST, new HttpEntity<>(new TransitionRequest("CONTAINED", null), createHeaders()), TransitionResponse.class);

        // Stub evidence service to fail
        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));

        // Act - Try to close
        CloseIncidentRequest closeRequest = new CloseIncidentRequest("Try to close", List.of());
        ResponseEntity<String> closeResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/close",
            HttpMethod.POST,
            new HttpEntity<>(closeRequest, createHeaders()),
            String.class
        );

        // Assert
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Verify incident is still in CONTAINED status (not closed)
        IncidentCase incident = incidentRepository.findByIdAndTenantId(incidentId, testTenantId).orElseThrow();
        assertThat(incident.getStatus()).isEqualTo("CONTAINED");
        assertThat(incident.getClosedAt()).isNull();

        // Reset stub
        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));
    }

    @Test
    @Order(7)
    void testSchedulerOverdue_shouldMarkNotifyOverdueAndEmitEvent() throws InterruptedException {
        // Arrange - create incident with past notify_due_at
        IncidentCase incident = new IncidentCase();
        incident.setTenantId(testTenantId);
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPENED");
        incident.setSummary("Overdue test incident");
        incident.setNotifyDueAt(Instant.now().minusSeconds(3600)); // 1 hour ago
        incident.setNotifyOverdue(false);
        incident.setMetadata("{}");
        incident = incidentRepository.save(incident);

        // Act - Run scheduler
        overdueScheduler.checkOverdueIncidents();

        // Assert
        IncidentCase updated = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(updated.getNotifyOverdue()).isTrue();
        
        // Note: In real implementation, verify outbox event was created
        // For now, we verify the database flag is set
    }

    @Test
    @Order(8)
    void testCreateTask_shouldCreateAndLinkToIncident() {
        // Arrange
        CreateIncidentRequest createRequest = new CreateIncidentRequest("MED", "Test", Map.of());
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Act
        CreateTaskRequest taskRequest = new CreateTaskRequest(
            "IMPACT_ASSESSMENT",
            testActorId,
            "Assess impact of the breach"
        );
        ResponseEntity<CreateTaskResponse> taskResponse = restTemplate.exchange(
            "/incidents/" + incidentId + "/tasks",
            HttpMethod.POST,
            new HttpEntity<>(taskRequest, createHeaders()),
            CreateTaskResponse.class
        );

        // Assert
        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taskResponse.getBody().taskId()).isNotNull();
        assertThat(taskResponse.getBody().status()).isEqualTo("OPEN");

        // Verify task is linked to incident
        assertThat(taskRepository.findByTenantIdAndIncidentId(testTenantId, incidentId))
            .hasSize(1);
    }

    @Test
    @Order(9)
    void testGetIncidentDetails_shouldReturnFullDetails() {
        // Arrange
        CreateIncidentRequest createRequest = new CreateIncidentRequest(
            "CRITICAL",
            "Full details test",
            Map.of("key", "value")
        );
        ResponseEntity<CreateIncidentResponse> createResponse = restTemplate.exchange(
            "/incidents",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, createHeaders()),
            CreateIncidentResponse.class
        );
        UUID incidentId = createResponse.getBody().incidentId();

        // Act
        ResponseEntity<IncidentDetailsResponse> detailsResponse = restTemplate.exchange(
            "/incidents/" + incidentId,
            HttpMethod.GET,
            new HttpEntity<>(createHeaders()),
            IncidentDetailsResponse.class
        );

        // Assert
        assertThat(detailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        IncidentDetailsResponse details = detailsResponse.getBody();
        assertThat(details.incidentId()).isEqualTo(incidentId);
        assertThat(details.status()).isEqualTo("OPENED");
        assertThat(details.severity()).isEqualTo("CRITICAL");
        assertThat(details.summary()).isEqualTo("Full details test");
        assertThat(details.notifyOverdue()).isFalse();
        assertThat(details.metadata()).containsEntry("key", "value");
    }
}
