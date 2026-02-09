package com.regulyn.dsar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.dsar.client.EvidenceServiceClient;
import com.regulyn.dsar.entity.AttachmentType;
import com.regulyn.dsar.entity.DsarAttachmentEntity;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.model.AttachmentReferenceRequest;
import com.regulyn.dsar.model.AttachmentResponse;
import com.regulyn.dsar.repository.DsarAttachmentRepository;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class AttachmentService {

    private final DsarRequestRepository dsarRequestRepository;
    private final DsarAttachmentRepository attachmentRepository;
    private final EvidenceServiceClient evidenceServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final long maxUploadBytes;

    public AttachmentService(
        DsarRequestRepository dsarRequestRepository,
        DsarAttachmentRepository attachmentRepository,
        EvidenceServiceClient evidenceServiceClient,
        OutboxWriter outboxWriter,
        AuditWriter auditWriter,
        ObjectMapper objectMapper,
        @Value("${dsar.attachments.maxUploadBytes:10485760}") long maxUploadBytes) {
        this.dsarRequestRepository = dsarRequestRepository;
        this.attachmentRepository = attachmentRepository;
        this.evidenceServiceClient = evidenceServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.maxUploadBytes = maxUploadBytes;
    }

    @Transactional
    public AttachmentResult addUploadAttachment(UUID tenantId, UUID userId, UUID dsarId,
                                                String idempotencyKey, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds max upload size");
        }

        DsarRequestEntity dsar = lockDsar(tenantId, dsarId);

        if (idempotencyKey != null) {
            Optional<DsarAttachmentEntity> existing = attachmentRepository
                .findByTenantIdAndDsarIdAndIdempotencyKey(tenantId, dsarId, idempotencyKey);
            if (existing.isPresent()) {
                return new AttachmentResult(mapResponse(existing.get()), true);
            }
        }

        if ("CLOSED".equals(dsar.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add attachments to CLOSED DSAR");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read file", e);
        }

        String sha256 = sha256Hex(bytes);
        long sizeBytes = bytes.length;
        int nextVersion = attachmentRepository.findMaxVersionByTenantIdAndDsarId(tenantId, dsarId) + 1;

        String artifactRef;
        try {
            artifactRef = evidenceServiceClient.storeAttachmentArtifact(
                tenantId, dsarId, file.getOriginalFilename(), file.getContentType(), sizeBytes, sha256, bytes);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence service unavailable", ex);
        }

        DsarAttachmentEntity entity = new DsarAttachmentEntity();
        entity.setTenantId(tenantId);
        entity.setDsarId(dsarId);
        entity.setAttachmentType(AttachmentType.UPLOAD);
        entity.setVersion(nextVersion);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setFilename(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(sizeBytes);
        entity.setSha256(sha256);
        entity.setArtifactRef(artifactRef);
        entity.setCreatedBy(userId);
        Map<String, Object> uploadMetadata = new HashMap<>();
        uploadMetadata.put("sizeBytes", sizeBytes);
        uploadMetadata.put("sha256", sha256);
        if (file.getOriginalFilename() != null) {
            uploadMetadata.put("filename", file.getOriginalFilename());
        }
        if (file.getContentType() != null) {
            uploadMetadata.put("contentType", file.getContentType());
        }
        entity.setMetadata(toJson(uploadMetadata));

        try {
            attachmentRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                Optional<DsarAttachmentEntity> existing = attachmentRepository
                    .findByTenantIdAndDsarIdAndIdempotencyKey(tenantId, dsarId, idempotencyKey);
                if (existing.isPresent()) {
                    return new AttachmentResult(mapResponse(existing.get()), true);
                }
            }
            throw e;
        }

        writeAttachmentAuditAndOutbox(entity, dsarId, tenantId, userId, artifactRef, sha256, null, null);

        return new AttachmentResult(mapResponse(entity), false);
    }

    @Transactional
    public AttachmentResult addReferenceAttachment(UUID tenantId, UUID userId, UUID dsarId,
                                                   String idempotencyKey, AttachmentReferenceRequest request) {
        if (request.getReferenceValue() == null || request.getReferenceValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceValue is required");
        }
        if (request.getReferenceHash() != null && !request.getReferenceHash().matches("^[a-fA-F0-9]{64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceHash must be 64-hex");
        }

        DsarRequestEntity dsar = lockDsar(tenantId, dsarId);

        if (idempotencyKey != null) {
            Optional<DsarAttachmentEntity> existing = attachmentRepository
                .findByTenantIdAndDsarIdAndIdempotencyKey(tenantId, dsarId, idempotencyKey);
            if (existing.isPresent()) {
                return new AttachmentResult(mapResponse(existing.get()), true);
            }
        }

        if ("CLOSED".equals(dsar.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add attachments to CLOSED DSAR");
        }

        int nextVersion = attachmentRepository.findMaxVersionByTenantIdAndDsarId(tenantId, dsarId) + 1;

        Map<String, Object> evidenceMetadata = new HashMap<>();
        evidenceMetadata.put("tenantId", tenantId.toString());
        evidenceMetadata.put("dsarId", dsarId.toString());
        evidenceMetadata.put("referenceRecorded", true);
        evidenceMetadata.put("referenceLength", request.getReferenceValue().length());
        evidenceMetadata.put("referenceKind", inferReferenceKind(request.getReferenceValue()));
        if (request.getReferenceHash() != null) {
            evidenceMetadata.put("referenceHash", request.getReferenceHash());
        }

        String recordedEvidenceArtifactRef;
        try {
            recordedEvidenceArtifactRef = evidenceServiceClient.storeReferenceRecordedArtifact(
                tenantId, dsarId, evidenceMetadata);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence service unavailable", ex);
        }

        DsarAttachmentEntity entity = new DsarAttachmentEntity();
        entity.setTenantId(tenantId);
        entity.setDsarId(dsarId);
        entity.setAttachmentType(AttachmentType.REFERENCE);
        entity.setVersion(nextVersion);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setFilename(request.getFilename());
        entity.setContentType(request.getContentType());
        entity.setReferenceValue(request.getReferenceValue());
        entity.setReferenceHash(request.getReferenceHash());
        entity.setRecordedEvidenceArtifactRef(recordedEvidenceArtifactRef);
        entity.setCreatedBy(userId);
        entity.setMetadata(toJson(Map.of(
            "referenceRecorded", true,
            "referenceLength", request.getReferenceValue().length(),
            "referenceKind", inferReferenceKind(request.getReferenceValue()),
            "referenceHash", request.getReferenceHash() != null ? request.getReferenceHash() : ""
        )));

        try {
            attachmentRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                Optional<DsarAttachmentEntity> existing = attachmentRepository
                    .findByTenantIdAndDsarIdAndIdempotencyKey(tenantId, dsarId, idempotencyKey);
                if (existing.isPresent()) {
                    return new AttachmentResult(mapResponse(existing.get()), true);
                }
            }
            throw e;
        }

        writeAttachmentAuditAndOutbox(entity, dsarId, tenantId, userId, null, null,
            request.getReferenceHash(), recordedEvidenceArtifactRef);

        return new AttachmentResult(mapResponse(entity), false);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(UUID tenantId, UUID dsarId) {
        if (dsarRequestRepository.findByRequestIdPkAndTenantId(dsarId, tenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DSAR not found");
        }

        List<DsarAttachmentEntity> entities = attachmentRepository
            .findByTenantIdAndDsarIdOrderByCreatedAtAsc(tenantId, dsarId);

        List<AttachmentResponse> responses = new ArrayList<>();
        for (DsarAttachmentEntity entity : entities) {
            responses.add(mapResponse(entity));
        }
        return responses;
    }

    private DsarRequestEntity lockDsar(UUID tenantId, UUID dsarId) {
        return dsarRequestRepository.findByRequestIdPkAndTenantIdForUpdate(dsarId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DSAR not found"));
    }

    private AttachmentResponse mapResponse(DsarAttachmentEntity entity) {
        AttachmentResponse response = new AttachmentResponse();
        response.setAttachmentId(entity.getId());
        response.setDsarId(entity.getDsarId());
        response.setType(entity.getAttachmentType().name());
        response.setVersion(entity.getVersion());
        response.setFilename(entity.getFilename());
        response.setContentType(entity.getContentType());
        response.setSizeBytes(entity.getSizeBytes());
        response.setSha256(entity.getSha256());
        response.setArtifactRef(entity.getArtifactRef());
        response.setReferenceHash(entity.getReferenceHash());
        response.setRecordedEvidenceArtifactRef(entity.getRecordedEvidenceArtifactRef());
        response.setCreatedAt(entity.getCreatedAt());
        response.setCreatedBy(entity.getCreatedBy());
        return response;
    }

    private void writeAttachmentAuditAndOutbox(DsarAttachmentEntity entity, UUID dsarId, UUID tenantId, UUID userId,
                                               String artifactRef, String sha256, String referenceHash,
                                               String recordedEvidenceArtifactRef) {
        Map<String, Object> addedPayload = new HashMap<>();
        addedPayload.put("tenantId", tenantId.toString());
        addedPayload.put("dsarId", dsarId.toString());
        addedPayload.put("attachmentId", entity.getId().toString());
        addedPayload.put("attachmentType", entity.getAttachmentType().name());
        addedPayload.put("version", entity.getVersion());
        addedPayload.put("createdBy", userId.toString());
        addedPayload.put("createdAt", entity.getCreatedAt().toString());
        if (sha256 != null) {
            addedPayload.put("sha256", sha256);
            addedPayload.put("artifactRef", artifactRef);
        }
        if (referenceHash != null) {
            addedPayload.put("referenceHash", referenceHash);
        }

        writeAudit("DSAR_ATTACHMENT_ADDED", entity.getId().toString(), tenantId, userId, addedPayload);
        writeOutboxEvent("dsar.attachment_added", entity.getId().toString(), addedPayload);

        Map<String, Object> artifactPayload = new HashMap<>();
        artifactPayload.put("tenantId", tenantId.toString());
        artifactPayload.put("dsarId", dsarId.toString());
        artifactPayload.put("attachmentId", entity.getId().toString());
        artifactPayload.put("attachmentType", entity.getAttachmentType().name());
        artifactPayload.put("createdAt", entity.getCreatedAt().toString());
        if (sha256 != null) {
            artifactPayload.put("sha256", sha256);
            artifactPayload.put("artifactRef", artifactRef);
        }
        if (recordedEvidenceArtifactRef != null) {
            artifactPayload.put("recordedEvidenceArtifactRef", recordedEvidenceArtifactRef);
        }

        writeAudit("DSAR_ATTACHMENT_ARTIFACT_STORED", entity.getId().toString(), tenantId, userId, artifactPayload);
        writeOutboxEvent("dsar.attachment_artifact_stored", entity.getId().toString(), artifactPayload);
    }

    private void writeAudit(String eventType, String entityId, UUID tenantId, UUID userId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = sha256Hex(payloadJson.getBytes(StandardCharsets.UTF_8));

            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(eventType)
                .entityType("dsar_attachment")
                .entityId(entityId)
                .payloadHash(payloadHash)
                .build();

            auditWriter.write(auditEvent);
        } catch (Exception e) {
            System.err.println("Failed to write audit event: " + e.getMessage());
        }
    }

    private void writeOutboxEvent(String eventType, String entityId, Map<String, Object> payload) {
        EventEnvelopeV1 event = EventFactory.create(
            eventType,
            "dsar-grievance-service",
            "dsar_attachment",
            entityId,
            payload
        );
        outboxWriter.write(event);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(bytes));
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

    private String inferReferenceKind(String referenceValue) {
        String value = referenceValue.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return "URL";
        }
        return "DOC_ID";
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record AttachmentResult(AttachmentResponse response, boolean idempotent) {}
}