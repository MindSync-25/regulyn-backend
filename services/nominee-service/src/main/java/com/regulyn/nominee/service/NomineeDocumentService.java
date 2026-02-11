package com.regulyn.nominee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.nominee.audit.NomineeAuditWriter;
import com.regulyn.nominee.client.EvidenceClient;
import com.regulyn.nominee.dto.NomineeDocumentRefRequest;
import com.regulyn.nominee.dto.NomineeDocumentResponse;
import com.regulyn.nominee.entity.ClaimDocument;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.entity.NomineeDocument;
import com.regulyn.nominee.repository.ClaimDocumentRepository;
import com.regulyn.nominee.repository.NomineeClaimRepository;
import com.regulyn.nominee.repository.NomineeDocumentRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NomineeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(NomineeDocumentService.class);
    private static final String ENTITY_TYPE = "NOMINEE_DOCUMENT";
    private static final String SOURCE_TYPE_UPLOAD = "UPLOAD";
    private static final String SOURCE_TYPE_REF = "PRESIGNED_REF";
    private static final String STEP_EXCEPTION = "VERIFICATION_EXCEPTION";

    private final NomineeDocumentRepository nomineeDocumentRepository;
    private final NomineeRepository nomineeRepository;
    private final NomineeClaimRepository nomineeClaimRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final EvidenceClient evidenceClient;
    private final NomineeAuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final long maxSizeBytes;

    public NomineeDocumentService(
            NomineeDocumentRepository nomineeDocumentRepository,
            NomineeRepository nomineeRepository,
            NomineeClaimRepository nomineeClaimRepository,
            ClaimDocumentRepository claimDocumentRepository,
            EvidenceClient evidenceClient,
            NomineeAuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:nominee-service}") String serviceName,
            @Value("${nominee.documents.maxSizeBytes:10485760}") long maxSizeBytes) {
        this.nomineeDocumentRepository = nomineeDocumentRepository;
        this.nomineeRepository = nomineeRepository;
        this.nomineeClaimRepository = nomineeClaimRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        this.maxSizeBytes = maxSizeBytes;
    }

    public NomineeDocumentResponse uploadDocument(
            UUID tenantId,
            UUID nomineeId,
            UUID userId,
            MultipartFile file,
            String verificationStep,
            UUID claimId,
            String notes,
            String idempotencyKey) {
        if (verificationStep == null || verificationStep.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationStep is required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        String trimmedKey = normalizeIdempotencyKey(idempotencyKey);

        requireNominee(tenantId, nomineeId);
        requireClaimIfPresent(tenantId, nomineeId, claimId);

        if (trimmedKey != null) {
            Optional<NomineeDocument> existing = nomineeDocumentRepository
                    .findFirstByTenantIdAndNomineeIdAndIdempotencyKey(tenantId, nomineeId, trimmedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        long declaredSize = file.getSize();
        if (declaredSize > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size");
        }

        FileHashResult hashResult = hashToTempFile(file);
        try {
            Optional<NomineeDocument> existingByHash = nomineeDocumentRepository
                    .findFirstByTenantIdAndNomineeIdAndSha256Hash(tenantId, nomineeId, hashResult.sha256);
            if (existingByHash.isPresent()) {
                return toResponse(existingByHash.get());
            }

            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
            String correlationId = nomineeId + ":" + verificationStep + ":" + hashResult.sha256;

            EvidenceClient.EvidenceArtifactResponse artifact = evidenceClient.createArtifact(
                    tenantId,
                    "NOMINEE_VERIFICATION_DOC",
                    correlationId,
                    filename,
                    contentType,
                    hashResult.sizeBytes,
                    hashResult.sha256,
                    new FileSystemResource(hashResult.tempFile.toFile())
            );

                return persistDocument(
                    tenantId,
                    nomineeId,
                    userId,
                    null,
                    claimId,
                    verificationStep,
                    filename,
                    contentType,
                    hashResult.sizeBytes,
                    artifact.artifactRef(),
                    artifact.sha256(),
                    SOURCE_TYPE_UPLOAD,
                    notes,
                    trimmedKey
            );
        } finally {
            deleteTempFile(hashResult.tempFile);
        }
    }

    public NomineeDocumentResponse registerDocumentRef(
            UUID tenantId,
            UUID nomineeId,
            UUID userId,
            NomineeDocumentRefRequest request,
            String idempotencyKey) {
        String trimmedKey = normalizeIdempotencyKey(idempotencyKey);

        requireNominee(tenantId, nomineeId);
        requireClaimIfPresent(tenantId, nomineeId, request.getClaimId());

        if (request.getSha256Hash() == null || request.getSha256Hash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sha256Hash is required");
        }
        if (!isValidSha256(request.getSha256Hash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sha256Hash must be 64 lowercase hex characters");
        }
        if (request.getSizeBytes() == null || request.getSizeBytes() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sizeBytes must be non-negative");
        }
        if (request.getSizeBytes() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size");
        }

        if (trimmedKey != null) {
            Optional<NomineeDocument> existing = nomineeDocumentRepository
                    .findFirstByTenantIdAndNomineeIdAndIdempotencyKey(tenantId, nomineeId, trimmedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Optional<NomineeDocument> existingByHash = nomineeDocumentRepository
                .findFirstByTenantIdAndNomineeIdAndSha256Hash(tenantId, nomineeId, request.getSha256Hash());
        if (existingByHash.isPresent()) {
            return toResponse(existingByHash.get());
        }

            return persistDocument(
                tenantId,
                nomineeId,
                userId,
                null,
                request.getClaimId(),
                request.getVerificationStep(),
                request.getFilename(),
                request.getContentType(),
                request.getSizeBytes(),
                request.getArtifactRef(),
                request.getSha256Hash(),
                SOURCE_TYPE_REF,
                request.getNotes(),
                trimmedKey
        );
    }

    public NomineeDocumentResponse registerVerificationException(
            UUID tenantId,
            UUID nomineeId,
            String approvedBy,
            String exceptionReason,
            String notes,
            String artifactRef,
            String sha256Hash,
            String filename,
            String contentType,
            long sizeBytes,
            UUID claimId,
            String idempotencyKey) {
        String trimmedKey = normalizeIdempotencyKey(idempotencyKey);

        requireNominee(tenantId, nomineeId);
        requireClaimIfPresent(tenantId, nomineeId, claimId);

        if (sha256Hash == null || sha256Hash.isBlank() || !isValidSha256(sha256Hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sha256Hash is required and must be 64 lowercase hex chars");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentType is required");
        }
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (sizeBytes < 0 || sizeBytes > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size");
        }

        if (trimmedKey != null) {
            Optional<NomineeDocument> existing = nomineeDocumentRepository
                    .findFirstByTenantIdAndNomineeIdAndIdempotencyKey(tenantId, nomineeId, trimmedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Optional<NomineeDocument> existingByHash = nomineeDocumentRepository
                .findFirstByTenantIdAndNomineeIdAndSha256Hash(tenantId, nomineeId, sha256Hash);
        if (existingByHash.isPresent()) {
            return toResponse(existingByHash.get());
        }

        String mergedNotes = mergeExceptionNotes(exceptionReason, notes);

        NomineeDocumentResponse response = persistDocument(
                tenantId,
                nomineeId,
            null,
            approvedBy,
                claimId,
                STEP_EXCEPTION,
                filename,
                contentType,
                sizeBytes,
                artifactRef,
                sha256Hash,
                SOURCE_TYPE_REF,
                mergedNotes,
                trimmedKey
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("nomineeId", nomineeId.toString());
        if (claimId != null) {
            payload.put("claimId", claimId.toString());
        }
        payload.put("nomineeDocumentId", response.getNomineeDocumentId().toString());
        payload.put("artifactRef", artifactRef);
        payload.put("sha256Hash", sha256Hash);
        payload.put("approvedBy", approvedBy);
        payload.put("createdAt", response.getUploadedAt() != null ? response.getUploadedAt().toString() : Instant.now().toString());

        writeAuditAndOutbox(
                tenantId,
                null,
                "NOMINEE_VERIFICATION_EXCEPTION_RECORDED",
                "nominee.verification_exception_recorded",
                response.getNomineeDocumentId(),
                payload,
                trimmedKey
        );

        return response;
    }

    @Transactional
        protected NomineeDocumentResponse persistDocument(
            UUID tenantId,
            UUID nomineeId,
            UUID userId,
            String uploadedByOverride,
            UUID claimId,
            String verificationStep,
            String filename,
            String contentType,
            long sizeBytes,
            String artifactRef,
            String sha256,
            String sourceType,
            String notes,
            String idempotencyKey) {
        NomineeDocument doc = new NomineeDocument();
        doc.setTenantId(tenantId);
        doc.setNomineeId(nomineeId);
        doc.setClaimId(claimId);
        doc.setVerificationStep(verificationStep);
        doc.setArtifactRef(artifactRef);
        doc.setSha256Hash(sha256);
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setSizeBytes(sizeBytes);
        doc.setSourceType(sourceType);
        if (uploadedByOverride != null && !uploadedByOverride.isBlank()) {
            doc.setUploadedBy(uploadedByOverride);
        } else {
            doc.setUploadedBy(userId != null ? userId.toString() : null);
        }
        doc.setIdempotencyKey(idempotencyKey);
        doc.setDocNotes(notes);

        NomineeDocument saved = nomineeDocumentRepository.save(doc);

        if (claimId != null) {
            linkClaimDocument(saved, claimId, verificationStep, artifactRef, userId, notes, contentType, sizeBytes, sha256);
        }

        Map<String, Object> payload = buildPayload(saved, filename, contentType, sizeBytes, artifactRef, sha256);
        writeAuditAndOutbox(tenantId, userId, "NOMINEE_DOC_UPLOADED", "nominee.doc_uploaded", saved.getId(), payload, idempotencyKey);
        writeAuditAndOutbox(tenantId, userId, "NOMINEE_DOC_ARTIFACT_STORED", "nominee.doc_artifact_stored", saved.getId(), payload, idempotencyKey);

        return toResponse(saved);
    }

    private void writeAuditAndOutbox(
            UUID tenantId,
            UUID userId,
            String action,
            String eventType,
            UUID entityId,
            Map<String, Object> payload,
            String idempotencyKey) {
        JsonNode metadata = objectMapper.valueToTree(payload);
        String payloadHash = EventHasher.sha256(EventJson.toCanonicalJson(payload));

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(userId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                .service(serviceName)
                .action(action)
                .entityType(ENTITY_TYPE)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();

        auditWriter.writeOrThrow(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(userId != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType(ENTITY_TYPE);
        envelope.setEntityId(entityId.toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(buildCorrelationId(entityId, idempotencyKey));
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }

    private void linkClaimDocument(
            NomineeDocument doc,
            UUID claimId,
            String verificationStep,
            String artifactRef,
            UUID userId,
            String notes,
            String contentType,
            long sizeBytes,
            String sha256) {
        List<ClaimDocument> existing = claimDocumentRepository.findByClaimIdAndNomineeDocumentId(claimId, doc.getId());
        if (!existing.isEmpty()) {
            return;
        }
        ClaimDocument claimDocument = new ClaimDocument();
        claimDocument.setClaimId(claimId);
        claimDocument.setDocType(verificationStep);
        claimDocument.setStorageUrl(artifactRef);
        claimDocument.setUploadedBy(userId);
        claimDocument.setNomineeDocumentId(doc.getId());
        claimDocument.setMetadata(buildClaimDocumentMetadata(notes, contentType, sizeBytes, sha256));
        claimDocumentRepository.save(claimDocument);
    }

    private String buildClaimDocumentMetadata(String notes, String contentType, long sizeBytes, String sha256) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (notes != null && !notes.isBlank()) {
                metadata.put("notes", notes);
            }
            metadata.put("contentType", contentType);
            metadata.put("sizeBytes", sizeBytes);
            metadata.put("sha256Hash", sha256);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> buildPayload(
            NomineeDocument doc,
            String filename,
            String contentType,
            long sizeBytes,
            String artifactRef,
            String sha256) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", doc.getTenantId().toString());
        payload.put("nomineeId", doc.getNomineeId().toString());
        if (doc.getClaimId() != null) {
            payload.put("claimId", doc.getClaimId().toString());
        }
        payload.put("nomineeDocumentId", doc.getId().toString());
        payload.put("verificationStep", doc.getVerificationStep());
        payload.put("artifactRef", artifactRef);
        payload.put("sha256Hash", sha256);
        payload.put("filename", filename);
        payload.put("contentType", contentType);
        payload.put("sizeBytes", sizeBytes);
        payload.put("uploadedAt", doc.getUploadedAt() != null ? doc.getUploadedAt().toString() : Instant.now().toString());
        return payload;
    }

    private Nominee requireNominee(UUID tenantId, UUID nomineeId) {
        Nominee nominee = nomineeRepository.findById(nomineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nominee not found"));
        if (!tenantId.equals(nominee.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nominee not found");
        }
        return nominee;
    }

    private void requireClaimIfPresent(UUID tenantId, UUID nomineeId, UUID claimId) {
        if (claimId == null) {
            return;
        }
        NomineeClaim claim = nomineeClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        if (!tenantId.equals(claim.getTenantId()) || !nomineeId.equals(claim.getNomineeId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }
    }

    private NomineeDocumentResponse toResponse(NomineeDocument doc) {
        NomineeDocumentResponse response = new NomineeDocumentResponse();
        response.setNomineeDocumentId(doc.getId());
        response.setTenantId(doc.getTenantId());
        response.setNomineeId(doc.getNomineeId());
        response.setClaimId(doc.getClaimId());
        response.setVerificationStep(doc.getVerificationStep());
        response.setArtifactRef(doc.getArtifactRef());
        response.setSha256Hash(doc.getSha256Hash());
        response.setFilename(doc.getFilename());
        response.setContentType(doc.getContentType());
        response.setSizeBytes(doc.getSizeBytes());
        response.setSourceType(doc.getSourceType());
        response.setUploadedAt(doc.getUploadedAt());
        return response;
    }

    private String mergeExceptionNotes(String exceptionReason, String notes) {
        StringBuilder builder = new StringBuilder();
        if (exceptionReason != null && !exceptionReason.isBlank()) {
            builder.append("ExceptionReason: ").append(exceptionReason.trim());
        }
        if (notes != null && !notes.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append("Notes: ").append(notes.trim());
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private boolean isValidSha256(String sha256) {
        if (sha256 == null || sha256.length() != 64) {
            return false;
        }
        for (int i = 0; i < sha256.length(); i++) {
            char c = sha256.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private FileHashResult hashToTempFile(MultipartFile file) {
        Path tempFile = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            tempFile = Files.createTempFile("nominee-doc-", ".upload");
            long total = 0;
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                 OutputStream outputStream = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = digestStream.read(buffer)) != -1) {
                    total += read;
                    if (total > maxSizeBytes) {
                        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            String sha256 = bytesToHex(digest.digest());
            return new FileHashResult(sha256, total, tempFile);
        } catch (ResponseStatusException ex) {
            deleteTempFile(tempFile);
            throw ex;
        } catch (IOException | NoSuchAlgorithmException ex) {
            deleteTempFile(tempFile);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", ex);
        }
    }

    private void deleteTempFile(Path tempFile) {
        try {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException ex) {
            log.warn("Failed to delete temp file: {}", tempFile, ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String buildCorrelationId(UUID entityId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return "nominee-doc-" + entityId;
    }

    private static class FileHashResult {
        private final String sha256;
        private final long sizeBytes;
        private final Path tempFile;

        private FileHashResult(String sha256, long sizeBytes, Path tempFile) {
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.tempFile = tempFile;
        }
    }
}
