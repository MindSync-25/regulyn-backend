package com.regulyn.dsar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.dsar.client.EvidenceServiceClient;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.entity.DsarStatusHistory;
import com.regulyn.dsar.model.*;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.dsar.repository.DsarStatusHistoryRepository;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DsarWorkflowService {
    
    private final DsarRequestRepository dsarRequestRepository;
    private final DsarStatusHistoryRepository statusHistoryRepository;
    private final DsarStateMachine stateMachine;
    private final EvidenceServiceClient evidenceServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    
    public DsarWorkflowService(
            DsarRequestRepository dsarRequestRepository,
            DsarStatusHistoryRepository statusHistoryRepository,
            DsarStateMachine stateMachine,
            EvidenceServiceClient evidenceServiceClient,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.dsarRequestRepository = dsarRequestRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.stateMachine = stateMachine;
        this.evidenceServiceClient = evidenceServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public CreateDsarResponse createDsar(CreateDsarRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID userId = context.getUserId();
        
        // Check idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<DsarRequestEntity> existing = dsarRequestRepository
                .findByTenantIdAndDataPrincipalIdAndIdempotencyKey(
                    tenantId, 
                    request.getDataPrincipalId(), 
                    request.getIdempotencyKey()
                );
            if (existing.isPresent()) {
                DsarRequestEntity existingEntity = existing.get();
                return new CreateDsarResponse(
                    existingEntity.getRequestIdPk(),
                    existingEntity.getStatus(),
                    existingEntity.getDueAt()
                );
            }
        }
        
        // Determine if approval is required
        Boolean requiresApproval = request.getRequiresApproval();
        if (requiresApproval == null) {
            // Default: DELETE requests require approval
            requiresApproval = "DELETE".equals(request.getRequestType());
        }
        
        // Create DSAR entity
        DsarRequestEntity entity = new DsarRequestEntity();
        entity.setTenantId(tenantId);
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setDataPrincipalId(request.getDataPrincipalId());
        entity.setRequestType(request.getRequestType());
        entity.setStatus("RECEIVED");
        entity.setRequesterEmail(""); // Will be populated from data principal lookup
        entity.setCreatedBy(userId);
        entity.setRequiresApproval(requiresApproval);
        entity.setIdempotencyKey(request.getIdempotencyKey());
        
        // Set details JSON
        try {
            String detailsJson = request.getDetails() != null ? 
                objectMapper.writeValueAsString(request.getDetails()) : "{}";
            entity.setDetailsJson(detailsJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize details", e);
        }
        
        dsarRequestRepository.save(entity);
        
        // Write audit event
        writeAudit("dsar.created", entity.getRequestIdPk().toString(), 
            Map.of("requestType", request.getRequestType(), "status", "RECEIVED"));
        
        // Write outbox event
        writeOutboxEvent("dsar.created", entity.getRequestIdPk().toString(), 
            Map.of(
                "dsarId", entity.getRequestIdPk().toString(),
                "requestType", request.getRequestType(),
                "status", "RECEIVED",
                "dataPrincipalId", request.getDataPrincipalId().toString(),
                "requiresApproval", requiresApproval
            )
        );
        
        return new CreateDsarResponse(entity.getRequestIdPk(), entity.getStatus(), entity.getDueAt());
    }
    
    @Transactional
    public AssignDsarResponse assignDsar(UUID dsarId, AssignDsarRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = getDsarEntity(dsarId, context.getTenantId());
        
        // Only allow assignment if status is RECEIVED
        if (!"RECEIVED".equals(entity.getStatus())) {
            throw new IllegalStateException("Can only assign DSAR in RECEIVED status. Current: " + entity.getStatus());
        }
        
        entity.setAssignedTo(request.getAssignedTo());
        entity.setStatus("IN_REVIEW");
        dsarRequestRepository.save(entity);
        
        // Record status history
        recordStatusChange(entity, "RECEIVED", "IN_REVIEW", "Assigned to reviewer", context.getUserId());
        
        // Write audit event
        writeAudit("dsar.assigned", dsarId.toString(), 
            Map.of("assignedTo", request.getAssignedTo().toString(), "status", "IN_REVIEW"));
        
        // Write outbox event
        writeOutboxEvent("dsar.assigned", dsarId.toString(), 
            Map.of(
                "dsarId", dsarId.toString(),
                "assignedTo", request.getAssignedTo().toString(),
                "status", "IN_REVIEW"
            )
        );
        
        return new AssignDsarResponse(dsarId, request.getAssignedTo(), "IN_REVIEW");
    }
    
    @Transactional
    public TransitionDsarResponse transitionStatus(UUID dsarId, TransitionDsarRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = getDsarEntity(dsarId, context.getTenantId());
        
        String currentStatus = entity.getStatus();
        String toStatus = request.getToStatus();
        
        // Validate transition
        if (!stateMachine.isValidTransition(currentStatus, toStatus)) {
            throw new IllegalStateException(
                String.format("Invalid transition from %s to %s. Allowed: %s",
                    currentStatus, toStatus, stateMachine.getAllowedTransitions(currentStatus))
            );
        }
        
        entity.setStatus(toStatus);
        dsarRequestRepository.save(entity);
        
        // Record status history
        recordStatusChange(entity, currentStatus, toStatus, request.getReason(), context.getUserId());
        
        // Write audit event
        writeAudit("dsar.status_changed", dsarId.toString(), 
            Map.of("fromStatus", currentStatus, "toStatus", toStatus));
        
        // Write outbox event
        writeOutboxEvent("dsar.status_changed", dsarId.toString(), 
            Map.of(
                "dsarId", dsarId.toString(),
                "fromStatus", currentStatus,
                "toStatus", toStatus,
                "reason", request.getReason() != null ? request.getReason() : ""
            )
        );
        
        return new TransitionDsarResponse(dsarId, toStatus);
    }
    
    @Transactional
    public ApproveDsarResponse approveDsar(UUID dsarId, ApproveDsarRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = getDsarEntity(dsarId, context.getTenantId());
        
        // Validate that approval is required
        if (!Boolean.TRUE.equals(entity.getRequiresApproval())) {
            throw new IllegalStateException("This DSAR does not require approval");
        }
        
        // TODO: Role-based access control check (DPO/REVIEWER/TENANT_ADMIN)
        // For now, we'll trust that the caller has permission
        
        boolean approved = "APPROVE".equals(request.getDecision());
        String newStatus = approved ? "APPROVED" : "REJECTED";
        
        entity.setStatus(newStatus);
        entity.setApprovedBy(context.getUserId());
        entity.setApprovedAt(Instant.now());
        dsarRequestRepository.save(entity);
        
        // Record status history
        recordStatusChange(entity, entity.getStatus(), newStatus, request.getReason(), context.getUserId());
        
        // Write audit event
        String eventType = approved ? "dsar.approved" : "dsar.rejected";
        writeAudit(eventType, dsarId.toString(), 
            Map.of("decision", request.getDecision(), "status", newStatus));
        
        // Write outbox event
        writeOutboxEvent(eventType, dsarId.toString(), 
            Map.of(
                "dsarId", dsarId.toString(),
                "approved", approved,
                "status", newStatus,
                "approvedBy", context.getUserId().toString(),
                "reason", request.getReason() != null ? request.getReason() : ""
            )
        );
        
        return new ApproveDsarResponse(dsarId, approved, newStatus);
    }
    
    @Transactional
    public CloseDsarResponse closeDsar(UUID dsarId, CloseDsarRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = getDsarEntity(dsarId, context.getTenantId());
        
        // Validate that closure is allowed
        if (!stateMachine.canClose(entity.getStatus())) {
            throw new IllegalStateException(
                String.format("Cannot close DSAR in status %s. Must be COMPLETED, APPROVED, or REJECTED",
                    entity.getStatus())
            );
        }
        
        // Create evidence bundle
        List<String> evidenceIds = request.getIncludeEvidenceIds() != null 
            ? request.getIncludeEvidenceIds().stream().map(UUID::toString).collect(Collectors.toList())
            : new ArrayList<>();
        
        // Always include a summary evidence record for the DSAR itself
        // (In real implementation, you'd create this evidence record first)
        
        UUID bundleId;
        try {
            bundleId = evidenceServiceClient.createBundle(
                "DSAR",
                "DSAR",
                dsarId.toString(),
                String.format("DSAR Request %s - %s", entity.getRequestType(), dsarId),
                evidenceIds
            );
        } catch (Exception e) {
            // If evidence service is unavailable, return 503
            throw new RuntimeException("Evidence service unavailable. Cannot close DSAR without evidence bundle.", e);
        }
        
        entity.setStatus("CLOSED");
        entity.setClosedAt(Instant.now());
        entity.setCloseEvidenceBundleId(bundleId);
        entity.setCloseNotes(request.getClosureNotes());
        dsarRequestRepository.save(entity);
        
        // Record status history
        recordStatusChange(entity, entity.getStatus(), "CLOSED", request.getClosureNotes(), context.getUserId());
        
        // Write audit event
        writeAudit("dsar.closed", dsarId.toString(), 
            Map.of("status", "CLOSED", "evidenceBundleId", bundleId.toString()));
        
        // Write outbox event
        writeOutboxEvent("dsar.closed", dsarId.toString(), 
            Map.of(
                "dsarId", dsarId.toString(),
                "status", "CLOSED",
                "evidenceBundleId", bundleId.toString(),
                "closedAt", entity.getClosedAt().toString()
            )
        );
        
        return new CloseDsarResponse(dsarId, "CLOSED", bundleId);
    }
    
    @Transactional(readOnly = true)
    public DsarDetailResponse getDsar(UUID dsarId) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = getDsarEntity(dsarId, context.getTenantId());
        return mapToDetailResponse(entity);
    }
    
    @Transactional(readOnly = true)
    public Page<DsarDetailResponse> searchDsars(
            String status, 
            String requestType, 
            UUID dataPrincipalId, 
            int page, 
            int size) {
        
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<DsarRequestEntity> results;
        
        if (status != null) {
            results = dsarRequestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (requestType != null) {
            results = dsarRequestRepository.findByTenantIdAndRequestType(tenantId, requestType, pageable);
        } else if (dataPrincipalId != null) {
            results = dsarRequestRepository.findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId, pageable);
        } else {
            results = dsarRequestRepository.findByTenantId(tenantId, pageable);
        }
        
        return results.map(this::mapToDetailResponse);
    }
    
    private DsarRequestEntity getDsarEntity(UUID dsarId, UUID tenantId) {
        return dsarRequestRepository.findByRequestIdPkAndTenantId(dsarId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("DSAR not found: " + dsarId));
    }
    
    private void recordStatusChange(DsarRequestEntity entity, String fromStatus, String toStatus, 
                                    String reason, UUID changedBy) {
        DsarStatusHistory history = new DsarStatusHistory();
        history.setTenantId(entity.getTenantId());
        history.setDsarId(entity.getRequestIdPk());
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        statusHistoryRepository.save(history);
    }
    
    private void writeAudit(String eventType, String entityId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = computeHash(payloadJson);
            
            TenantContext context = TenantContextHolder.getContext();
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(context.getTenantId())
                .actorId(context.getUserId())
                .actorType(AuditEvent.ActorType.USER)
                .action(eventType)
                .entityType("dsar_request")
                .entityId(entityId)
                .payloadHash(payloadHash)
                .build();
            
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            // Log but don't fail the operation
            System.err.println("Failed to write audit event: " + e.getMessage());
        }
    }
    
    private void writeOutboxEvent(String eventType, String entityId, Map<String, Object> payload) {
        EventEnvelopeV1 event = EventFactory.create(
            eventType,
            "dsar-grievance-service",
            "dsar_request",
            entityId,
            payload
        );
        outboxWriter.write(event);
    }
    
    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private DsarDetailResponse mapToDetailResponse(DsarRequestEntity entity) {
        DsarDetailResponse response = new DsarDetailResponse();
        response.setDsarId(entity.getRequestIdPk());
        response.setTenantId(entity.getTenantId());
        response.setDataPrincipalId(entity.getDataPrincipalId());
        response.setRequestType(entity.getRequestType());
        response.setStatus(entity.getStatus());
        response.setRequiresApproval(entity.getRequiresApproval());
        response.setAssignedTo(entity.getAssignedTo());
        response.setApprovedBy(entity.getApprovedBy());
        response.setApprovedAt(entity.getApprovedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setDueAt(entity.getDueAt());
        response.setClosedAt(entity.getClosedAt());
        response.setCloseEvidenceBundleId(entity.getCloseEvidenceBundleId());
        response.setCloseNotes(entity.getCloseNotes());
        response.setSlaBreached(entity.getSlaBreached());
        
        // Parse details JSON
        try {
            if (entity.getDetailsJson() != null && !entity.getDetailsJson().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = objectMapper.readValue(entity.getDetailsJson(), Map.class);
                response.setDetails(details);
            }
        } catch (JsonProcessingException e) {
            response.setDetails(new HashMap<>());
        }
        
        return response;
    }
}
