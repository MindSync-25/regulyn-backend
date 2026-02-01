package com.regulyn.employee.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.dto.*;
import com.regulyn.employee.model.Employee;
import com.regulyn.employee.model.EmployeeRequest;
import com.regulyn.employee.model.EmployeeRequestStatusHistory;
import com.regulyn.employee.repository.EmployeeRepository;
import com.regulyn.employee.repository.EmployeeRequestRepository;
import com.regulyn.employee.repository.EmployeeRequestStatusHistoryRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class EmployeeRequestService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeRequestService.class);

    private final EmployeeRequestRepository employeeRequestRepository;
    private final EmployeeRequestStatusHistoryRepository statusHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxWriter outboxWriter;

    // State machine transition rules
    private static final Map<EmployeeRequest.RequestStatus, Set<EmployeeRequest.RequestStatus>> ALLOWED_TRANSITIONS = Map.of(
            EmployeeRequest.RequestStatus.RECEIVED, Set.of(EmployeeRequest.RequestStatus.IN_REVIEW),
            EmployeeRequest.RequestStatus.IN_REVIEW, Set.of(EmployeeRequest.RequestStatus.NEEDS_INFO, EmployeeRequest.RequestStatus.APPROVED, 
                    EmployeeRequest.RequestStatus.REJECTED, EmployeeRequest.RequestStatus.IN_PROGRESS),
            EmployeeRequest.RequestStatus.NEEDS_INFO, Set.of(EmployeeRequest.RequestStatus.IN_REVIEW),
            EmployeeRequest.RequestStatus.APPROVED, Set.of(EmployeeRequest.RequestStatus.IN_PROGRESS, EmployeeRequest.RequestStatus.COMPLETED),
            EmployeeRequest.RequestStatus.IN_PROGRESS, Set.of(EmployeeRequest.RequestStatus.COMPLETED, EmployeeRequest.RequestStatus.FAILED),
            EmployeeRequest.RequestStatus.FAILED, Set.of(EmployeeRequest.RequestStatus.IN_PROGRESS, EmployeeRequest.RequestStatus.CLOSED),
            EmployeeRequest.RequestStatus.COMPLETED, Set.of(EmployeeRequest.RequestStatus.CLOSED),
            EmployeeRequest.RequestStatus.REJECTED, Set.of(EmployeeRequest.RequestStatus.CLOSED),
            EmployeeRequest.RequestStatus.CLOSED, Set.of() // Terminal state
    );

    public EmployeeRequestService(
            EmployeeRequestRepository employeeRequestRepository,
            EmployeeRequestStatusHistoryRepository statusHistoryRepository,
            EmployeeRepository employeeRepository,
            OutboxWriter outboxWriter) {
        this.employeeRequestRepository = employeeRequestRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.outboxWriter = outboxWriter;
    }

    public EmployeeRequestResponse createRequest(CreateEmployeeRequestRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            // Check idempotency
            if (request.getIdempotencyKey() != null) {
                Optional<EmployeeRequest> existing = employeeRequestRepository
                        .findByTenantIdAndEmployeeIdAndIdempotencyKey(tenantId, request.getEmployeeId(), request.getIdempotencyKey());
                if (existing.isPresent()) {
                    log.info("Idempotent request found for key {}", request.getIdempotencyKey());
                    return toEmployeeRequestResponse(existing.get());
                }
            }

            // Validate employee exists
            Employee employee = employeeRepository.findByTenantIdAndEmployeeId(tenantId, request.getEmployeeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee not found"));

            EmployeeRequest employeeRequest = new EmployeeRequest();
            employeeRequest.setTenantId(tenantId);
            employeeRequest.setEmployeeId(request.getEmployeeId());
            employeeRequest.setRequestType(request.getRequestType());
            employeeRequest.setStatus(EmployeeRequest.RequestStatus.RECEIVED);
            employeeRequest.setDetailsJson(request.getDetails());
            employeeRequest.setRequiresApproval(request.getRequiresApproval());
            employeeRequest.setIdempotencyKey(request.getIdempotencyKey());
            // dueAt is set in @PrePersist (90 days from creation)

            employeeRequest = employeeRequestRepository.save(employeeRequest);

            // Record initial status change
            recordStatusChange(employeeRequest, null, EmployeeRequest.RequestStatus.RECEIVED, 
                    null, "Request created");

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("requestId", employeeRequest.getRequestId().toString());
            eventPayload.put("employeeId", employeeRequest.getEmployeeId().toString());
            eventPayload.put("requestType", employeeRequest.getRequestType().name());
            eventPayload.put("status", employeeRequest.getStatus().name());
            
            EventEnvelopeV1 event = EventFactory.create(
                    "employee_request.created",
                    "employee-data-service",
                    "employee_request",
                    employeeRequest.getRequestId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            log.info("Created employee request {} for tenant {}", employeeRequest.getRequestId(), tenantId);
            return toEmployeeRequestResponse(employeeRequest);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating employee request for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create employee request", e);
        }
    }

    public EmployeeRequestResponse assignRequest(UUID requestId, AssignRequestRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            EmployeeRequest employeeRequest = employeeRequestRepository.findByTenantIdAndRequestId(tenantId, requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee request not found"));

            if (employeeRequest.getStatus() != EmployeeRequest.RequestStatus.RECEIVED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Request can only be assigned when status is RECEIVED");
            }

            employeeRequest.setAssignedTo(request.getAssignedTo());
            employeeRequest.setStatus(EmployeeRequest.RequestStatus.IN_REVIEW);
            employeeRequest.setUpdatedAt(Instant.now());
            employeeRequest = employeeRequestRepository.save(employeeRequest);

            // Record status change
            recordStatusChange(employeeRequest, EmployeeRequest.RequestStatus.RECEIVED, 
                    EmployeeRequest.RequestStatus.IN_REVIEW, null, "Assigned to reviewer");

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("requestId", employeeRequest.getRequestId().toString());
            eventPayload.put("assignedTo", request.getAssignedTo().toString());
            eventPayload.put("status", employeeRequest.getStatus().name());
            
            EventEnvelopeV1 event = EventFactory.create(
                    "employee_request.assigned",
                    "employee-data-service",
                    "employee_request",
                    employeeRequest.getRequestId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toEmployeeRequestResponse(employeeRequest);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error assigning employee request {} for tenant {}", requestId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to assign employee request", e);
        }
    }

    public EmployeeRequestResponse approveRequest(UUID requestId, ApproveRequestRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            EmployeeRequest employeeRequest = employeeRequestRepository.findByTenantIdAndRequestId(tenantId, requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee request not found"));

            if (employeeRequest.getStatus() != EmployeeRequest.RequestStatus.IN_REVIEW) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Request can only be approved/rejected when status is IN_REVIEW");
            }

            if (!employeeRequest.getRequiresApproval()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "This request does not require approval");
            }

            EmployeeRequest.RequestStatus oldStatus = employeeRequest.getStatus();
            EmployeeRequest.RequestStatus newStatus;
            String eventType;

            if (request.getDecision() == ApproveRequestRequest.Decision.APPROVE) {
                newStatus = EmployeeRequest.RequestStatus.APPROVED;
                employeeRequest.setApprovedBy(TenantContextHolder.getTenantId()); // Using tenantId as approver for now
                employeeRequest.setApprovedAt(Instant.now());
                eventType = "employee_request.approved";
            } else {
                newStatus = EmployeeRequest.RequestStatus.REJECTED;
                eventType = "employee_request.rejected";
            }

            employeeRequest.setStatus(newStatus);
            employeeRequest.setUpdatedAt(Instant.now());
            employeeRequest = employeeRequestRepository.save(employeeRequest);

            // Record status change
            recordStatusChange(employeeRequest, oldStatus, newStatus, null, request.getReason());

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("requestId", employeeRequest.getRequestId().toString());
            eventPayload.put("decision", request.getDecision().name());
            eventPayload.put("status", employeeRequest.getStatus().name());
            
            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    "employee-data-service",
                    "employee_request",
                    employeeRequest.getRequestId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toEmployeeRequestResponse(employeeRequest);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error approving employee request {} for tenant {}", requestId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to approve employee request", e);
        }
    }

    public EmployeeRequestResponse transitionRequest(UUID requestId, TransitionRequestRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            EmployeeRequest employeeRequest = employeeRequestRepository.findByTenantIdAndRequestId(tenantId, requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee request not found"));

            EmployeeRequest.RequestStatus oldStatus = employeeRequest.getStatus();
            EmployeeRequest.RequestStatus newStatus = request.getToStatus();

            // Validate transition
            if (!isTransitionAllowed(oldStatus, newStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        String.format("Transition from %s to %s is not allowed", oldStatus, newStatus));
            }

            // Approval gating: if requiresApproval and trying to go to IN_PROGRESS, must be APPROVED first
            if (employeeRequest.getRequiresApproval() && 
                newStatus == EmployeeRequest.RequestStatus.IN_PROGRESS && 
                oldStatus != EmployeeRequest.RequestStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Request requires approval before transitioning to IN_PROGRESS");
            }

            employeeRequest.setStatus(newStatus);
            employeeRequest.setUpdatedAt(Instant.now());
            employeeRequest = employeeRequestRepository.save(employeeRequest);

            // Record status change
            recordStatusChange(employeeRequest, oldStatus, newStatus, null, request.getReason());

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("requestId", employeeRequest.getRequestId().toString());
            eventPayload.put("fromStatus", oldStatus.name());
            eventPayload.put("toStatus", newStatus.name());
            
            EventEnvelopeV1 event = EventFactory.create(
                    "employee_request.status_changed",
                    "employee-data-service",
                    "employee_request",
                    employeeRequest.getRequestId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toEmployeeRequestResponse(employeeRequest);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error transitioning employee request {} for tenant {}", requestId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to transition employee request", e);
        }
    }

    public EmployeeRequestResponse closeRequest(UUID requestId, CloseRequestRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            EmployeeRequest employeeRequest = employeeRequestRepository.findByTenantIdAndRequestId(tenantId, requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee request not found"));

            EmployeeRequest.RequestStatus oldStatus = employeeRequest.getStatus();
            
            // Can only close from terminal-ready states
            if (oldStatus != EmployeeRequest.RequestStatus.COMPLETED && 
                oldStatus != EmployeeRequest.RequestStatus.REJECTED && 
                oldStatus != EmployeeRequest.RequestStatus.FAILED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Request can only be closed from COMPLETED, REJECTED, or FAILED status");
            }

            // TODO: Create evidence bundle via EvidenceClient
            // For now, we'll skip evidence creation

            employeeRequest.setStatus(EmployeeRequest.RequestStatus.CLOSED);
            employeeRequest.setClosureNotes(request.getClosureNotes());
            employeeRequest.setClosedAt(Instant.now());
            employeeRequest.setUpdatedAt(Instant.now());
            employeeRequest = employeeRequestRepository.save(employeeRequest);

            // Record status change
            recordStatusChange(employeeRequest, oldStatus, EmployeeRequest.RequestStatus.CLOSED, 
                    null, request.getClosureNotes());

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("requestId", employeeRequest.getRequestId().toString());
            eventPayload.put("closedAt", employeeRequest.getClosedAt().toString());
            
            EventEnvelopeV1 event = EventFactory.create(
                    "employee_request.closed",
                    "employee-data-service",
                    "employee_request",
                    employeeRequest.getRequestId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toEmployeeRequestResponse(employeeRequest);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error closing employee request {} for tenant {}", requestId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to close employee request", e);
        }
    }

    @Transactional(readOnly = true)
    public EmployeeRequestResponse getRequest(UUID requestId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        EmployeeRequest request = employeeRequestRepository.findByTenantIdAndRequestId(tenantId, requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Employee request not found"));
        
        return toEmployeeRequestResponse(request);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeRequestResponse> listRequests(
            EmployeeRequest.RequestStatus status, 
            EmployeeRequest.RequestType requestType, 
            UUID employeeId, 
            Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        Page<EmployeeRequest> requestsPage;
        
        if (status != null && requestType != null && employeeId != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndStatusAndRequestTypeAndEmployeeId(
                    tenantId, status, requestType, employeeId, pageable);
        } else if (status != null && requestType != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndStatusAndRequestType(
                    tenantId, status, requestType, pageable);
        } else if (status != null && employeeId != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndStatusAndEmployeeId(
                    tenantId, status, employeeId, pageable);
        } else if (requestType != null && employeeId != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndRequestTypeAndEmployeeId(
                    tenantId, requestType, employeeId, pageable);
        } else if (status != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (requestType != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndRequestType(tenantId, requestType, pageable);
        } else if (employeeId != null) {
            requestsPage = employeeRequestRepository.findByTenantIdAndEmployeeId(tenantId, employeeId, pageable);
        } else {
            requestsPage = employeeRequestRepository.findByTenantId(tenantId, pageable);
        }
        
        return requestsPage.map(this::toEmployeeRequestResponse);
    }

    private boolean isTransitionAllowed(EmployeeRequest.RequestStatus from, EmployeeRequest.RequestStatus to) {
        Set<EmployeeRequest.RequestStatus> allowedStates = ALLOWED_TRANSITIONS.get(from);
        return allowedStates != null && allowedStates.contains(to);
    }

    private void recordStatusChange(EmployeeRequest request, EmployeeRequest.RequestStatus fromStatus, 
                                    EmployeeRequest.RequestStatus toStatus, UUID changedBy, String reason) {
        EmployeeRequestStatusHistory history = new EmployeeRequestStatusHistory();
        history.setTenantId(request.getTenantId());
        history.setRequestId(request.getRequestId());
        history.setFromStatus(fromStatus != null ? fromStatus.name() : "");
        history.setToStatus(toStatus.name());
        history.setChangedBy(changedBy);
        history.setReason(reason);
        // changedAt is set in @PrePersist
        statusHistoryRepository.save(history);
    }

    private EmployeeRequestResponse toEmployeeRequestResponse(EmployeeRequest request) {
        EmployeeRequestResponse response = new EmployeeRequestResponse();
        response.setRequestId(request.getRequestId());
        response.setTenantId(request.getTenantId());
        response.setEmployeeId(request.getEmployeeId());
        response.setRequestType(request.getRequestType());
        response.setStatus(request.getStatus());
        response.setDetailsJson(request.getDetailsJson());
        response.setRequiresApproval(request.getRequiresApproval());
        response.setAssignedTo(request.getAssignedTo());
        response.setApprovedBy(request.getApprovedBy());
        response.setApprovedAt(request.getApprovedAt());
        response.setCreatedAt(request.getCreatedAt());
        response.setUpdatedAt(request.getUpdatedAt());
        response.setClosedAt(request.getClosedAt());
        response.setDueAt(request.getDueAt());
        response.setSlaBreached(request.getSlaBreached());
        response.setIdempotencyKey(request.getIdempotencyKey());
        response.setClosureNotes(request.getClosureNotes());
        response.setEvidenceBundleId(request.getEvidenceBundleId());
        return response;
    }
}
