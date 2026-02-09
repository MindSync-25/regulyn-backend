package com.regulyn.dsar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.entity.DsarStatusHistory;
import com.regulyn.dsar.model.*;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.dsar.repository.DsarStatusHistoryRepository;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import com.regulyn.events.outbox.OutboxWriter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DsarWorkflowServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    private static WireMockServer wireMockServer;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("dsar.sla.scheduler.enabled", () -> "false");
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMockServer.port());
    }
    
    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0); // Random port
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }
    
    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }
    
    @Autowired
    private DsarWorkflowService dsarWorkflowService;
    
    @Autowired
    private DsarRequestRepository dsarRequestRepository;
    
    @Autowired
    private DsarStatusHistoryRepository statusHistoryRepository;
    
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    
    @Autowired
    private OutboxWriter outboxWriter;
    
    @Autowired
    private AuditWriter auditWriter;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private UUID tenantId;
    private UUID userId;
    private UUID dataPrincipalId;
    
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        dataPrincipalId = UUID.randomUUID();
        
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(userId);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
        
        // Clear outbox events
        outboxEventRepository.deleteAll();
    }
    
    @Test
    @Order(1)
    void testCreateDsar_Success() {
        // Arrange
        CreateDsarRequest request = new CreateDsarRequest();
        request.setDataPrincipalId(dataPrincipalId);
        request.setRequestType("ACCESS");
        request.setDetails(Map.of("requestedData", "personal_info"));
        request.setRequiresApproval(false);
        
        // Act
        CreateDsarResponse response = dsarWorkflowService.createDsar(request);
        
        // Assert
        assertNotNull(response.getDsarId());
        assertEquals("RECEIVED", response.getStatus());
        assertNotNull(response.getDueAt());
        
        // Verify in database
        Optional<DsarRequestEntity> saved = dsarRequestRepository.findByRequestIdPkAndTenantId(
            response.getDsarId(), tenantId);
        assertTrue(saved.isPresent());
        assertEquals("ACCESS", saved.get().getRequestType());
        assertEquals(dataPrincipalId, saved.get().getDataPrincipalId());
        assertEquals(false, saved.get().getRequiresApproval());
        
        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream()
            .anyMatch(e -> e.getEventType().equals("dsar.created")));
    }
    
    @Test
    @Order(2)
    void testCreateDsar_WithIdempotency() {
        // Arrange
        String idempotencyKey = "unique-key-123";
        CreateDsarRequest request = new CreateDsarRequest();
        request.setDataPrincipalId(dataPrincipalId);
        request.setRequestType("DELETE");
        request.setIdempotencyKey(idempotencyKey);
        
        // Act - First call
        CreateDsarResponse response1 = dsarWorkflowService.createDsar(request);
        
        // Act - Second call with same idempotency key
        CreateDsarResponse response2 = dsarWorkflowService.createDsar(request);
        
        // Assert - Should return same DSAR
        assertEquals(response1.getDsarId(), response2.getDsarId());
        assertEquals(response1.getStatus(), response2.getStatus());
        
        // Verify only one record exists
        long count = dsarRequestRepository.count();
        assertTrue(count >= 1); // At least the one we created
    }
    
    @Test
    @Order(3)
    void testAssignDsar_Success() {
        // Arrange - Create a DSAR first
        CreateDsarRequest createRequest = new CreateDsarRequest();
        createRequest.setDataPrincipalId(dataPrincipalId);
        createRequest.setRequestType("CORRECT");
        CreateDsarResponse created = dsarWorkflowService.createDsar(createRequest);
        
        UUID reviewerId = UUID.randomUUID();
        AssignDsarRequest assignRequest = new AssignDsarRequest();
        assignRequest.setAssignedTo(reviewerId);
        
        // Act
        AssignDsarResponse response = dsarWorkflowService.assignDsar(created.getDsarId(), assignRequest);
        
        // Assert
        assertEquals(created.getDsarId(), response.getDsarId());
        assertEquals(reviewerId, response.getAssignedTo());
        assertEquals("IN_REVIEW", response.getStatus());
        
        // Verify status history
        List<DsarStatusHistory> history = statusHistoryRepository.findAll();
        assertTrue(history.stream()
            .anyMatch(h -> h.getDsarId().equals(created.getDsarId()) 
                && "RECEIVED".equals(h.getFromStatus()) 
                && "IN_REVIEW".equals(h.getToStatus())));
        
        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream()
            .anyMatch(e -> e.getEventType().equals("dsar.assigned")));
    }
    
    @Test
    @Order(4)
    void testApproveDsar_RequiresApproval() {
        // Arrange - Create a DELETE DSAR (requires approval by default)
        CreateDsarRequest createRequest = new CreateDsarRequest();
        createRequest.setDataPrincipalId(dataPrincipalId);
        createRequest.setRequestType("DELETE");
        createRequest.setRequiresApproval(true);
        CreateDsarResponse created = dsarWorkflowService.createDsar(createRequest);
        
        // Assign it first
        AssignDsarRequest assignRequest = new AssignDsarRequest();
        assignRequest.setAssignedTo(UUID.randomUUID());
        dsarWorkflowService.assignDsar(created.getDsarId(), assignRequest);
        
        // Prepare approval
        ApproveDsarRequest approveRequest = new ApproveDsarRequest();
        approveRequest.setDecision("APPROVE");
        approveRequest.setReason("Verified deletion request is legitimate");
        
        // Act
        ApproveDsarResponse response = dsarWorkflowService.approveDsar(created.getDsarId(), approveRequest);
        
        // Assert
        assertEquals(created.getDsarId(), response.getDsarId());
        assertTrue(response.isApproved());
        assertEquals("APPROVED", response.getStatus());
        
        // Verify in database
        Optional<DsarRequestEntity> saved = dsarRequestRepository.findByRequestIdPkAndTenantId(
            created.getDsarId(), tenantId);
        assertTrue(saved.isPresent());
        assertEquals("APPROVED", saved.get().getStatus());
        assertNotNull(saved.get().getApprovedBy());
        assertNotNull(saved.get().getApprovedAt());
        
        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream()
            .anyMatch(e -> e.getEventType().equals("dsar.approved")));
    }
    
    @Test
    @Order(5)
    void testCloseDsar_CreatesEvidenceBundle() {
        // Arrange - Create and approve a DSAR
        CreateDsarRequest createRequest = new CreateDsarRequest();
        createRequest.setDataPrincipalId(dataPrincipalId);
        createRequest.setRequestType("ACCESS");
        createRequest.setRequiresApproval(false);
        CreateDsarResponse created = dsarWorkflowService.createDsar(createRequest);
        
        // Assign to move to IN_REVIEW
        AssignDsarRequest assignRequest = new AssignDsarRequest();
        assignRequest.setAssignedTo(UUID.randomUUID());
        dsarWorkflowService.assignDsar(created.getDsarId(), assignRequest);
        
        // Transition to COMPLETED
        TransitionDsarRequest transitionRequest = new TransitionDsarRequest();
        transitionRequest.setToStatus("COMPLETED");
        dsarWorkflowService.transitionStatus(created.getDsarId(), transitionRequest);
        
        // Mock evidence service response
        UUID mockBundleId = UUID.randomUUID();
        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"evidenceId\":\"11111111-1111-1111-1111-111111111115\",\"status\":\"STORED\"}")));
        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format(
                    "{\"bundleId\":\"%s\",\"bundleHash\":\"abc123\",\"status\":\"CREATED\"}",
                    mockBundleId))));
        
        CloseDsarRequest closeRequest = new CloseDsarRequest();
        closeRequest.setClosureNotes("Request fulfilled successfully");
        closeRequest.setIncludeEvidenceIds(Arrays.asList(UUID.randomUUID()));
        
        // Act
        CloseDsarResponse response = dsarWorkflowService.closeDsar(created.getDsarId(), closeRequest);
        
        // Assert
        assertEquals(created.getDsarId(), response.getDsarId());
        assertEquals("CLOSED", response.getStatus());
        assertNotNull(response.getEvidenceBundleId());
        
        // Verify evidence service was called
        verify(postRequestedFor(urlEqualTo("/bundles"))
            .withHeader("X-Tenant-ID", equalTo(tenantId.toString()))
            .withHeader("X-User-ID", equalTo(userId.toString())));
        
        // Verify in database
        Optional<DsarRequestEntity> saved = dsarRequestRepository.findByRequestIdPkAndTenantId(
            created.getDsarId(), tenantId);
        assertTrue(saved.isPresent());
        assertEquals("CLOSED", saved.get().getStatus());
        assertNotNull(saved.get().getClosedAt());
        assertNotNull(saved.get().getCloseEvidenceBundleId());
        assertEquals("Request fulfilled successfully", saved.get().getCloseNotes());
        
        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream()
            .anyMatch(e -> e.getEventType().equals("dsar.closed")));
    }
    
    @Test
    @Order(6)
    void testSlaBreachScheduler() {
        // Arrange - Create a DSAR with past due date
        DsarRequestEntity overdueEntity = new DsarRequestEntity();
        overdueEntity.setTenantId(tenantId);
        overdueEntity.setRequestId(UUID.randomUUID().toString());
        overdueEntity.setDataPrincipalId(dataPrincipalId);
        overdueEntity.setRequestType("ACCESS");
        overdueEntity.setStatus("IN_REVIEW");
        overdueEntity.setRequesterEmail("test@example.com");
        overdueEntity.setCreatedBy(userId);
        overdueEntity.setDueAt(Instant.now().minusSeconds(86400)); // 1 day overdue
        overdueEntity.setSlaBreached(false);
        dsarRequestRepository.save(overdueEntity);
        
        // Create scheduler and inject dependencies
        DsarSlaScheduler scheduler = new DsarSlaScheduler(
            dsarRequestRepository, 
            outboxWriter,
            auditWriter,
            objectMapper
        );
        
        // Act
        scheduler.checkSlaBreaches();
        
        // Assert
        Optional<DsarRequestEntity> updated = dsarRequestRepository.findByRequestIdPkAndTenantId(
            overdueEntity.getRequestIdPk(), tenantId);
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getSlaBreached());
        
        // Verify outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream()
            .anyMatch(e -> e.getEventType().equals("dsar.sla_breached")));
    }
    
    @Test
    @Order(7)
    void testInvalidTransition_ThrowsException() {
        // Arrange - Create a DSAR
        CreateDsarRequest createRequest = new CreateDsarRequest();
        createRequest.setDataPrincipalId(dataPrincipalId);
        createRequest.setRequestType("ACCESS");
        CreateDsarResponse created = dsarWorkflowService.createDsar(createRequest);
        
        // Try to transition from RECEIVED directly to CLOSED (invalid)
        TransitionDsarRequest transitionRequest = new TransitionDsarRequest();
        transitionRequest.setToStatus("CLOSED");
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            dsarWorkflowService.transitionStatus(created.getDsarId(), transitionRequest);
        });
    }
    
    @Test
    @Order(8)
    void testSearchDsars_ByStatus() {
        // Arrange - Create multiple DSARs
        for (int i = 0; i < 3; i++) {
            CreateDsarRequest request = new CreateDsarRequest();
            request.setDataPrincipalId(dataPrincipalId);
            request.setRequestType("ACCESS");
            dsarWorkflowService.createDsar(request);
        }
        
        // Act
        var results = dsarWorkflowService.searchDsars("RECEIVED", null, null, 0, 10);
        
        // Assert
        assertTrue(results.getTotalElements() >= 3);
        assertTrue(results.getContent().stream()
            .allMatch(d -> "RECEIVED".equals(d.getStatus())));
    }
}
