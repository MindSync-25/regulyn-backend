package com.regulyn.employee;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.config.TestSecurityConfig;
import com.regulyn.employee.dto.*;
import com.regulyn.employee.model.EmployeeRequest;
import com.regulyn.employee.model.EmployeeRequest.RequestStatus;
import com.regulyn.employee.model.EmployeeRequest.RequestType;
import com.regulyn.employee.model.EmployeeRequestStatusHistory;
import com.regulyn.employee.repository.EmployeeRepository;
import com.regulyn.employee.repository.EmployeeRequestRepository;
import com.regulyn.employee.repository.EmployeeRequestStatusHistoryRepository;
import com.regulyn.employee.service.EmployeeRequestService;
import com.regulyn.employee.service.EmployeeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestSecurityConfig.class)
class EmployeeRequestServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EmployeeRequestService employeeRequestService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRequestRepository employeeRequestRepository;

    @Autowired
    private EmployeeRequestStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private UUID tenantId;
    private EmployeeResponse testEmployee;

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
    }

    @AfterEach
    void tearDown() {
        statusHistoryRepository.deleteAll();
        employeeRequestRepository.deleteAll();
        employeeRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should return existing request when using same idempotency key")
    void testIdempotency() {
        // Given
        String idempotencyKey = "unique-key-123";
        CreateEmployeeRequestRequest request = new CreateEmployeeRequestRequest();
        request.setEmployeeId(testEmployee.getEmployeeId());
        request.setRequestType(RequestType.ACCESS);
        request.setIdempotencyKey(idempotencyKey);
        request.setDetails(Map.of("reason", "Data access request"));

        // When - Create first request
        EmployeeRequestResponse firstRequest = employeeRequestService.createRequest(request);

        // Create second request with same idempotency key
        EmployeeRequestResponse secondRequest = employeeRequestService.createRequest(request);

        // Then
        assertThat(secondRequest.getRequestId()).isEqualTo(firstRequest.getRequestId());
        assertThat(employeeRequestRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .isPresent()
                .get()
                .extracting(EmployeeRequest::getRequestId)
                .isEqualTo(firstRequest.getRequestId());

        // Verify only one request was created
        Page<EmployeeRequest> allRequests = employeeRequestRepository.findByTenantId(tenantId, Pageable.unpaged());
        assertThat(allRequests.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should block IN_PROGRESS transition when requires approval and not approved")
    void testApprovalGating() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.DELETE);
        createRequest.setRequiresApproval(true);
        createRequest.setDetails(Map.of("reason", "GDPR deletion"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);

        // Assign and move to IN_REVIEW
        AssignRequestRequest assignRequest = new AssignRequestRequest();
        assignRequest.setAssignedTo(UUID.randomUUID());
        employeeRequestService.assignRequest(request.getRequestId(), assignRequest);

        // When/Then - Try to transition to IN_PROGRESS without approval
        TransitionRequestRequest transitionRequest = new TransitionRequestRequest();
        transitionRequest.setToStatus(RequestStatus.IN_PROGRESS);
        transitionRequest.setReason("Starting work");

        assertThatThrownBy(() -> 
                employeeRequestService.transitionRequest(request.getRequestId(), transitionRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requires approval");
    }

    @Test
    @DisplayName("Should allow IN_PROGRESS transition after approval")
    void testApprovalAllowsProgress() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.CORRECT);
        createRequest.setRequiresApproval(true);
        createRequest.setDetails(Map.of("correction", "Update address"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);

        // Assign
        AssignRequestRequest assignRequest = new AssignRequestRequest();
        assignRequest.setAssignedTo(UUID.randomUUID());
        employeeRequestService.assignRequest(request.getRequestId(), assignRequest);

        // Approve
        ApproveRequestRequest approveRequest = new ApproveRequestRequest();
        approveRequest.setDecision(ApproveRequestRequest.Decision.APPROVE);
        approveRequest.setReason("Approved for processing");
        employeeRequestService.approveRequest(request.getRequestId(), approveRequest);

        // When - Transition to IN_PROGRESS
        TransitionRequestRequest transitionRequest = new TransitionRequestRequest();
        transitionRequest.setToStatus(RequestStatus.IN_PROGRESS);
        transitionRequest.setReason("Starting work");

        EmployeeRequestResponse updated = employeeRequestService.transitionRequest(
                request.getRequestId(), transitionRequest);

        // Then
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Should validate state machine transitions - valid transitions")
    void testValidStateMachineTransitions() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.ACCESS);
        createRequest.setRequiresApproval(false);
        createRequest.setDetails(Map.of("access", "Request data copy"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RECEIVED);

        // When/Then - Valid transition: RECEIVED -> IN_REVIEW
        TransitionRequestRequest toReview = new TransitionRequestRequest();
        toReview.setToStatus(RequestStatus.IN_REVIEW);
        toReview.setReason("Under review");

        EmployeeRequestResponse inReview = employeeRequestService.transitionRequest(
                request.getRequestId(), toReview);
        assertThat(inReview.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);

        // Valid transition: IN_REVIEW -> IN_PROGRESS
        TransitionRequestRequest toProgress = new TransitionRequestRequest();
        toProgress.setToStatus(RequestStatus.IN_PROGRESS);
        toProgress.setReason("Processing");

        EmployeeRequestResponse inProgress = employeeRequestService.transitionRequest(
                request.getRequestId(), toProgress);
        assertThat(inProgress.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);

        // Valid transition: IN_PROGRESS -> COMPLETED
        TransitionRequestRequest toCompleted = new TransitionRequestRequest();
        toCompleted.setToStatus(RequestStatus.COMPLETED);
        toCompleted.setReason("Done");

        EmployeeRequestResponse completed = employeeRequestService.transitionRequest(
                request.getRequestId(), toCompleted);
        assertThat(completed.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject invalid state machine transitions")
    void testInvalidStateMachineTransitions() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.ACCESS);
        createRequest.setRequiresApproval(false);
        createRequest.setDetails(Map.of("access", "Request"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);

        // When/Then - Invalid transition: RECEIVED -> COMPLETED (skipping states)
        TransitionRequestRequest invalidTransition = new TransitionRequestRequest();
        invalidTransition.setToStatus(RequestStatus.COMPLETED);
        invalidTransition.setReason("Invalid jump");

        assertThatThrownBy(() -> 
                employeeRequestService.transitionRequest(request.getRequestId(), invalidTransition))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Transition from RECEIVED to COMPLETED is not allowed");
    }

    @Test
    @DisplayName("Should reject transition from terminal CLOSED state")
    void testTerminalStateTransition() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.WITHDRAW);
        createRequest.setRequiresApproval(false);
        createRequest.setDetails(Map.of("withdrawal", "Cancel request"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);

        // Move through states to REJECTED -> CLOSED
        TransitionRequestRequest toReview = new TransitionRequestRequest();
        toReview.setToStatus(RequestStatus.IN_REVIEW);
        employeeRequestService.transitionRequest(request.getRequestId(), toReview);

        TransitionRequestRequest toRejected = new TransitionRequestRequest();
        toRejected.setToStatus(RequestStatus.REJECTED);
        employeeRequestService.transitionRequest(request.getRequestId(), toRejected);

        CloseRequestRequest closeRequest = new CloseRequestRequest();
        closeRequest.setClosureNotes("Closed");
        employeeRequestService.closeRequest(request.getRequestId(), closeRequest);

        // When/Then - Try to transition from CLOSED (should fail)
        TransitionRequestRequest fromClosed = new TransitionRequestRequest();
        fromClosed.setToStatus(RequestStatus.IN_REVIEW);

        assertThatThrownBy(() -> 
                employeeRequestService.transitionRequest(request.getRequestId(), fromClosed))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Transition from CLOSED to IN_REVIEW is not allowed");
    }

    @Test
    @DisplayName("Should track status history for all transitions")
    void testStatusHistoryTracking() {
        // Given
        CreateEmployeeRequestRequest createRequest = new CreateEmployeeRequestRequest();
        createRequest.setEmployeeId(testEmployee.getEmployeeId());
        createRequest.setRequestType(RequestType.ACCESS);
        createRequest.setRequiresApproval(false);
        createRequest.setDetails(Map.of("access", "Data request"));

        EmployeeRequestResponse request = employeeRequestService.createRequest(createRequest);

        // When - Make several transitions
        TransitionRequestRequest toReview = new TransitionRequestRequest();
        toReview.setToStatus(RequestStatus.IN_REVIEW);
        toReview.setReason("Review comment");
        employeeRequestService.transitionRequest(request.getRequestId(), toReview);

        TransitionRequestRequest toProgress = new TransitionRequestRequest();
        toProgress.setToStatus(RequestStatus.IN_PROGRESS);
        toProgress.setReason("Progress comment");
        employeeRequestService.transitionRequest(request.getRequestId(), toProgress);

        // Then
        List<EmployeeRequestStatusHistory> history = 
                statusHistoryRepository.findByTenantIdAndRequestIdOrderByChangedAtAsc(tenantId, request.getRequestId());

        assertThat(history).hasSize(3); // RECEIVED, IN_REVIEW, IN_PROGRESS

        // Verify first entry (creation)
        assertThat(history.get(0).getFromStatus()).isEmpty();
        assertThat(history.get(0).getToStatus()).isEqualTo(RequestStatus.RECEIVED.name());
        assertThat(history.get(0).getReason()).contains("Request created");

        // Verify second entry
        assertThat(history.get(1).getFromStatus()).isEqualTo(RequestStatus.RECEIVED.name());
        assertThat(history.get(1).getToStatus()).isEqualTo(RequestStatus.IN_REVIEW.name());
        assertThat(history.get(1).getReason()).isEqualTo("Review comment");

        // Verify third entry
        assertThat(history.get(2).getFromStatus()).isEqualTo(RequestStatus.IN_REVIEW.name());
        assertThat(history.get(2).getToStatus()).isEqualTo(RequestStatus.IN_PROGRESS.name());
        assertThat(history.get(2).getReason()).isEqualTo("Progress comment");
    }
}

