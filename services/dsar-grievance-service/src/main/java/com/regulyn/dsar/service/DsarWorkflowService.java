package com.regulyn.dsar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.dsar.client.EvidenceServiceClient;
import com.regulyn.dsar.entity.BundleRefStatus;
import com.regulyn.dsar.entity.DsarAttachmentEntity;
import com.regulyn.dsar.entity.DsarEvidenceBundleRefEntity;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.entity.DsarStatusHistory;
import com.regulyn.dsar.entity.DsarEscalationTaskEntity;
import com.regulyn.dsar.model.*;
import com.regulyn.dsar.repository.DsarAttachmentRepository;
import com.regulyn.dsar.repository.DsarEscalationTaskRepository;
import com.regulyn.dsar.repository.DsarEvidenceBundleRefRepository;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.dsar.repository.DsarStatusHistoryRepository;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class DsarWorkflowService {
    
    private final DsarRequestRepository dsarRequestRepository;
    private final DsarStatusHistoryRepository statusHistoryRepository;
    private final DsarStateMachine stateMachine;
    private final EvidenceServiceClient evidenceServiceClient;
    private final DsarAttachmentRepository attachmentRepository;
    private final DsarEscalationTaskRepository escalationTaskRepository;
    private final DsarEvidenceBundleRefRepository bundleRefRepository;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    
    public DsarWorkflowService(
            DsarRequestRepository dsarRequestRepository,
            DsarStatusHistoryRepository statusHistoryRepository,
            DsarStateMachine stateMachine,
            EvidenceServiceClient evidenceServiceClient,
            DsarAttachmentRepository attachmentRepository,
            DsarEscalationTaskRepository escalationTaskRepository,
            DsarEvidenceBundleRefRepository bundleRefRepository,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.dsarRequestRepository = dsarRequestRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.stateMachine = stateMachine;
        this.evidenceServiceClient = evidenceServiceClient;
        this.attachmentRepository = attachmentRepository;
        this.escalationTaskRepository = escalationTaskRepository;
        this.bundleRefRepository = bundleRefRepository;
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
        return closeDsar(dsarId, request, null);
    }

    @Transactional
    public CloseDsarResponse closeDsar(UUID dsarId, CloseDsarRequest request, String idempotencyKey) {
        TenantContext context = TenantContextHolder.getContext();
        DsarRequestEntity entity = dsarRequestRepository
            .findByRequestIdPkAndTenantIdForUpdate(dsarId, context.getTenantId())
            .orElseThrow(() -> new IllegalArgumentException("DSAR not found: " + dsarId));

        UUID closeEventId = deriveCloseEventId(context.getTenantId(), dsarId, idempotencyKey);

        if ("CLOSED".equals(entity.getStatus())) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return bundleRefRepository.findByTenantIdAndDsarIdAndCloseEventId(
                        context.getTenantId(), dsarId, closeEventId)
                    .filter(ref -> ref.getBundleRef() != null)
                    .map(ref -> new CloseDsarResponse(dsarId, "CLOSED", entity.getCloseEvidenceBundleId()))
                    .orElseThrow(() -> new IllegalStateException(
                        "DSAR already closed with different close event"));
            }
            throw new IllegalStateException("DSAR is already CLOSED");
        }
        
        // Validate that closure is allowed
        if (!stateMachine.canClose(entity.getStatus())) {
            throw new IllegalStateException(
                String.format("Cannot close DSAR in status %s. Must be COMPLETED, APPROVED, or REJECTED",
                    entity.getStatus())
            );
        }
        
        DsarEvidenceBundleRefEntity bundleRef = getOrCreateBundleRef(
            context.getTenantId(), dsarId, closeEventId);

        UUID bundleId;
        String bundleHash;

        Instant generatedAt = Instant.now();
        Instant closeAt = Instant.now();

        if (bundleRef.getStatus() == BundleRefStatus.BUNDLE_STORED && bundleRef.getBundleRef() != null) {
            bundleId = UUID.fromString(bundleRef.getBundleRef());
            bundleHash = bundleRef.getBundleSha256();
        } else {
            List<DsarAttachmentEntity> attachments = attachmentRepository
                .findByTenantIdAndDsarIdOrderByCreatedAtAsc(context.getTenantId(), dsarId);
            List<DsarEscalationTaskEntity> escalations = escalationTaskRepository
                .findByTenantIdAndDsarIdOrderByReachedAtAsc(context.getTenantId(), dsarId);
            List<DsarStatusHistory> history = statusHistoryRepository
                .findByTenantIdAndDsarIdOrderByChangedAtDesc(context.getTenantId(), dsarId);

            Map<String, Object> bundleMetadata = buildCloseBundleMetadata(entity, history, attachments, escalations,
                request != null ? request.getClosureNotes() : null, closeEventId, generatedAt, closeAt);

            List<String> evidenceIds = buildEvidenceIds(request, attachments);

            try {
                String closeEvidenceId = evidenceServiceClient.storeCloseEvidence(
                    context.getTenantId(), dsarId, closeEventId,
                    Map.of(
                        "generatedAt", generatedAt.toString(),
                        "attachmentCount", attachments.size(),
                        "escalationCount", escalations.size()
                    )
                );
                evidenceIds.add(0, closeEvidenceId);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable. Cannot close DSAR without evidence bundle.", e);
            }

            try {
                var response = evidenceServiceClient.createBundleWithMetadata(
                    "DSAR",
                    "DSAR",
                    dsarId.toString(),
                    String.format("DSAR Request %s - %s", entity.getRequestType(), dsarId),
                    "DSAR closure evidence bundle",
                    evidenceIds,
                    null,
                    bundleMetadata
                );
                bundleId = response.getBundleId();
                bundleHash = response.getBundleHash();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable. Cannot close DSAR without evidence bundle.", e);
            }

            bundleRef.setBundleRef(bundleId.toString());
            bundleRef.setBundleSha256(bundleHash);
            bundleRef.setStatus(BundleRefStatus.BUNDLE_STORED);
            bundleRefRepository.save(bundleRef);

            writeAudit("DSAR_EVIDENCE_BUNDLE_UPDATED", dsarId.toString(),
                Map.of(
                    "tenantId", context.getTenantId().toString(),
                    "dsarId", dsarId.toString(),
                    "closeEventId", closeEventId.toString(),
                    "bundleRef", bundleId.toString(),
                    "bundleSha256", bundleHash,
                    "attachmentCount", attachments.size(),
                    "escalationCount", escalations.size(),
                    "generatedAt", generatedAt.toString()
                ));

            writeOutboxEvent("dsar.evidence_bundle_updated", dsarId.toString(),
                Map.of(
                    "tenantId", context.getTenantId().toString(),
                    "dsarId", dsarId.toString(),
                    "closeEventId", closeEventId.toString(),
                    "bundleRef", bundleId.toString(),
                    "bundleSha256", bundleHash,
                    "attachmentCount", attachments.size(),
                    "escalationCount", escalations.size(),
                    "generatedAt", generatedAt.toString()
                ));
        }

        String previousStatus = entity.getStatus();
        entity.setStatus("CLOSED");
        entity.setClosedAt(closeAt);
        entity.setCloseEvidenceBundleId(bundleId);
        entity.setCloseNotes(request != null ? request.getClosureNotes() : null);
        dsarRequestRepository.save(entity);

        recordStatusChange(entity, previousStatus, "CLOSED",
            request != null ? request.getClosureNotes() : null, context.getUserId());
        
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

    private DsarEvidenceBundleRefEntity getOrCreateBundleRef(UUID tenantId, UUID dsarId, UUID closeEventId) {
        Optional<DsarEvidenceBundleRefEntity> existing = bundleRefRepository
            .findByTenantIdAndDsarIdAndCloseEventId(tenantId, dsarId, closeEventId);
        if (existing.isPresent()) {
            return existing.get();
        }

        DsarEvidenceBundleRefEntity entity = new DsarEvidenceBundleRefEntity();
        entity.setTenantId(tenantId);
        entity.setDsarId(dsarId);
        entity.setCloseEventId(closeEventId);
        entity.setStatus(BundleRefStatus.CREATED);

        try {
            bundleRefRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            return bundleRefRepository.findByTenantIdAndDsarIdAndCloseEventId(tenantId, dsarId, closeEventId)
                .orElseThrow(() -> ex);
        }

        writeAudit("DSAR_EVIDENCE_BUNDLE_REF_CREATED", dsarId.toString(),
            Map.of(
                "tenantId", tenantId.toString(),
                "dsarId", dsarId.toString(),
                "closeEventId", closeEventId.toString(),
                "status", BundleRefStatus.CREATED.name()
            ));

        writeOutboxEvent("dsar.evidence_bundle_ref_created", dsarId.toString(),
            Map.of(
                "tenantId", tenantId.toString(),
                "dsarId", dsarId.toString(),
                "closeEventId", closeEventId.toString(),
                "status", BundleRefStatus.CREATED.name()
            ));

        return entity;
    }

    private UUID deriveCloseEventId(UUID tenantId, UUID dsarId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return UUID.randomUUID();
        }
        String seed = tenantId + ":" + dsarId + ":" + idempotencyKey;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> buildEvidenceIds(CloseDsarRequest request, List<DsarAttachmentEntity> attachments) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request != null && request.getIncludeEvidenceIds() != null) {
            request.getIncludeEvidenceIds().forEach(id -> ids.add(id.toString()));
        }
        for (DsarAttachmentEntity attachment : attachments) {
            String evidenceId = parseEvidenceId(attachment.getArtifactRef());
            if (evidenceId != null) {
                ids.add(evidenceId);
            }
            String recordedId = parseEvidenceId(attachment.getRecordedEvidenceArtifactRef());
            if (recordedId != null) {
                ids.add(recordedId);
            }
        }
        return new ArrayList<>(ids);
    }

    private String parseEvidenceId(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String value = ref.startsWith("evidence://") ? ref.substring("evidence://".length()) : ref;
        try {
            UUID.fromString(value);
            return value;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> buildCloseBundleMetadata(
        DsarRequestEntity entity,
        List<DsarStatusHistory> history,
        List<DsarAttachmentEntity> attachments,
        List<DsarEscalationTaskEntity> escalations,
        String closeNotes,
        UUID closeEventId,
        Instant generatedAt,
        Instant closeAt) {

        Map<String, Object> dsar = new LinkedHashMap<>();
        dsar.put("dsarId", entity.getRequestIdPk().toString());
        dsar.put("tenantId", entity.getTenantId().toString());
        dsar.put("dataPrincipalId", entity.getDataPrincipalId() != null ? entity.getDataPrincipalId().toString() : null);
        dsar.put("requestType", entity.getRequestType());
        dsar.put("status", "CLOSED");
        dsar.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dsar.put("dueAt", entity.getDueAt() != null ? entity.getDueAt().toString() : null);
        dsar.put("closedAt", closeAt != null ? closeAt.toString() : null);
        dsar.put("requiresApproval", entity.getRequiresApproval());
        dsar.put("approvedBy", entity.getApprovedBy() != null ? entity.getApprovedBy().toString() : null);
        dsar.put("approvedAt", entity.getApprovedAt() != null ? entity.getApprovedAt().toString() : null);
        dsar.put("closeNotesHash", closeNotes != null && !closeNotes.isBlank() ? hashString(closeNotes) : null);

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("requiresApproval", entity.getRequiresApproval());
        approval.put("approvedBy", entity.getApprovedBy() != null ? entity.getApprovedBy().toString() : null);
        approval.put("approvedAt", entity.getApprovedAt() != null ? entity.getApprovedAt().toString() : null);
        ApprovalDecision approvalDecision = resolveApprovalDecision(history);
        approval.put("decision", approvalDecision.decision());
        approval.put("reasonHash", approvalDecision.reasonHash());

        List<Map<String, Object>> timeline = new ArrayList<>();
        List<DsarStatusHistory> ordered = new ArrayList<>(history);
        ordered.sort(Comparator.comparing(DsarStatusHistory::getChangedAt));
        for (DsarStatusHistory entry : ordered) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("from", entry.getFromStatus());
            item.put("to", entry.getToStatus());
            item.put("at", entry.getChangedAt() != null ? entry.getChangedAt().toString() : null);
            item.put("by", entry.getChangedBy() != null ? entry.getChangedBy().toString() : null);
            item.put("reasonHash", entry.getReason() != null && !entry.getReason().isBlank()
                ? hashString(entry.getReason()) : null);
            timeline.add(item);
        }

        List<Map<String, Object>> attachmentPayloads = new ArrayList<>();
        for (DsarAttachmentEntity attachment : attachments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attachmentId", attachment.getId().toString());
            item.put("type", attachment.getAttachmentType().name());
            item.put("version", attachment.getVersion());
            item.put("filename", attachment.getFilename());
            item.put("contentType", attachment.getContentType());
            item.put("sizeBytes", attachment.getSizeBytes());
            item.put("sha256", attachment.getSha256());
            item.put("artifactRef", attachment.getArtifactRef());
            item.put("referenceHash", attachment.getReferenceHash());
            item.put("recordedEvidenceArtifactRef", attachment.getRecordedEvidenceArtifactRef());
            item.put("createdAt", attachment.getCreatedAt() != null ? attachment.getCreatedAt().toString() : null);
            item.put("createdBy", attachment.getCreatedBy() != null ? attachment.getCreatedBy().toString() : null);
            attachmentPayloads.add(item);
        }

        List<Map<String, Object>> escalationPayloads = new ArrayList<>();
        for (DsarEscalationTaskEntity task : escalations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("threshold", task.getThreshold().name());
            item.put("dueAt", task.getDueAt() != null ? task.getDueAt().toString() : null);
            item.put("reachedAt", task.getReachedAt() != null ? task.getReachedAt().toString() : null);
            item.put("status", task.getStatus().name());
            item.put("notificationRequestId", task.getNotificationRequestId());
            item.put("providerMessageId", task.getProviderMessageId());
            item.put("lastError", task.getLastError());
            escalationPayloads.add(item);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bundleType", "DSAR");
        metadata.put("referenceType", "DSAR");
        metadata.put("referenceId", entity.getRequestIdPk().toString());
        metadata.put("closeEventId", closeEventId.toString());
        metadata.put("generatedAt", generatedAt.toString());
        metadata.put("dsar", dsar);
        metadata.put("approval", approval);
        metadata.put("timeline", timeline);
        metadata.put("attachments", attachmentPayloads);
        metadata.put("escalations", escalationPayloads);
        return metadata;
    }

    private ApprovalDecision resolveApprovalDecision(List<DsarStatusHistory> history) {
        if (history == null) {
            return new ApprovalDecision(null, null);
        }
        for (DsarStatusHistory entry : history) {
            if ("APPROVED".equals(entry.getToStatus())) {
                return new ApprovalDecision("APPROVED", entry.getReason() != null && !entry.getReason().isBlank()
                    ? hashString(entry.getReason()) : null);
            }
            if ("REJECTED".equals(entry.getToStatus())) {
                return new ApprovalDecision("REJECTED", entry.getReason() != null && !entry.getReason().isBlank()
                    ? hashString(entry.getReason()) : null);
            }
        }
        return new ApprovalDecision(null, null);
    }

    private String hashString(String input) {
        return computeHash(input);
    }

    private record ApprovalDecision(String decision, String reasonHash) {}
    
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
