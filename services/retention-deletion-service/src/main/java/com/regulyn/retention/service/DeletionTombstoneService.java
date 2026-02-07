package com.regulyn.retention.service;

import com.regulyn.retention.cascade.CascadeEventWriter;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.entity.DeletionTombstone;
import com.regulyn.retention.enums.DeletionTombstoneStatus;
import com.regulyn.retention.integration.EvidenceServiceClient;
import com.regulyn.retention.model.TombstoneCreateRequest;
import com.regulyn.retention.model.TombstoneRemoveRequest;
import com.regulyn.retention.model.TombstoneResponse;
import com.regulyn.retention.repository.DeletionTombstoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeletionTombstoneService {

    private final DeletionTombstoneRepository tombstoneRepository;
    private final EvidenceServiceClient evidenceServiceClient;
    private final CascadeEventWriter eventWriter;

    public DeletionTombstoneService(
            DeletionTombstoneRepository tombstoneRepository,
            EvidenceServiceClient evidenceServiceClient,
            CascadeEventWriter eventWriter) {
        this.tombstoneRepository = tombstoneRepository;
        this.evidenceServiceClient = evidenceServiceClient;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public TombstoneResponse createTombstone(UUID tenantId, UUID actorId, TombstoneCreateRequest request) {
        Optional<DeletionTombstone> existing = tombstoneRepository.findByTenantIdAndSubjectTypeAndSubjectRef(
                tenantId,
                request.getSubjectType(),
                request.getSubjectRef());

        if (existing.isPresent() && existing.get().getTombstoneStatus() == DeletionTombstoneStatus.ACTIVE) {
            return toResponse(existing.get());
        }

        UUID tombstoneId = existing.map(DeletionTombstone::getTombstoneId).orElse(UUID.randomUUID());
        String hash = computeHash(request.getSubjectType() + "|" + request.getSubjectRef() + "|" + request.getReason());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subjectType", request.getSubjectType());
        metadata.put("subjectRef", request.getSubjectRef());
        metadata.put("reason", request.getReason());
        if (request.getCreatedFromDeletionId() != null) {
            metadata.put("createdFromDeletionId", request.getCreatedFromDeletionId());
        }

        UUID artifactId = evidenceServiceClient.createArtifact(
                tenantId,
                actorId,
                "TOMBSTONE_CREATE",
                "TOMBSTONE",
                tombstoneId,
                hash,
                null,
                metadata
        ).resolveArtifactId();

        DeletionTombstone tombstone = existing.orElseGet(DeletionTombstone::new);
        tombstone.setTombstoneId(tombstoneId);
        tombstone.setTenantId(tenantId);
        tombstone.setSubjectType(request.getSubjectType());
        tombstone.setSubjectRef(request.getSubjectRef());
        tombstone.setTombstoneStatus(DeletionTombstoneStatus.ACTIVE);
        tombstone.setReason(request.getReason());
        tombstone.setCreatedFromDeletionId(request.getCreatedFromDeletionId());
        tombstone.setCreatedArtifactId(artifactId);
        tombstone.setRemovedArtifactId(null);
        tombstone.setRemovedAt(null);
        tombstone.setRemovedBy(null);
        tombstone.setCreatedBy(actorId);
        tombstone.setUpdatedBy(actorId);

        tombstone = tombstoneRepository.save(tombstone);

        writeTombstoneCreatedEvents(tenantId, actorId, tombstone);

        return toResponse(tombstone);
    }

    @Transactional
    public TombstoneResponse removeTombstone(UUID tenantId, UUID actorId, UUID tombstoneId, TombstoneRemoveRequest request) {
        DeletionTombstone tombstone = tombstoneRepository.findById(tombstoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tombstone not found"));

        if (!tombstone.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tombstone not found");
        }

        if (tombstone.getTombstoneStatus() == DeletionTombstoneStatus.REMOVED) {
            return toResponse(tombstone);
        }

        String hash = computeHash(tombstoneId + "|" + request.getReason());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tombstoneId", tombstoneId);
        metadata.put("subjectType", tombstone.getSubjectType());
        metadata.put("subjectRef", tombstone.getSubjectRef());
        metadata.put("reason", request.getReason());

        UUID artifactId = evidenceServiceClient.createArtifact(
                tenantId,
                actorId,
                "TOMBSTONE_REMOVE",
                "TOMBSTONE",
                tombstoneId,
                hash,
                null,
                metadata
        ).resolveArtifactId();

        tombstone.setTombstoneStatus(DeletionTombstoneStatus.REMOVED);
        tombstone.setRemovedArtifactId(artifactId);
        tombstone.setRemovedAt(Instant.now());
        tombstone.setRemovedBy(actorId);
        tombstone.setUpdatedBy(actorId);

        tombstone = tombstoneRepository.save(tombstone);

        writeTombstoneRemovedEvents(tenantId, actorId, tombstone);

        return toResponse(tombstone);
    }

    private void writeTombstoneCreatedEvents(UUID tenantId, UUID actorId, DeletionTombstone tombstone) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("tombstoneId", tombstone.getTombstoneId());
        payload.put("subjectType", tombstone.getSubjectType());
        payload.put("subjectRef", tombstone.getSubjectRef());
        payload.put("reason", tombstone.getReason());
        payload.put("artifactId", tombstone.getCreatedArtifactId());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_TOMBSTONED,
                "DeletionTombstone",
                tombstone.getTombstoneId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_TOMBSTONED,
                "DeletionTombstone",
                tombstone.getTombstoneId(),
                payload,
                "tombstone-created:" + tombstone.getTombstoneId()
        );
    }

    private void writeTombstoneRemovedEvents(UUID tenantId, UUID actorId, DeletionTombstone tombstone) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("tombstoneId", tombstone.getTombstoneId());
        payload.put("subjectType", tombstone.getSubjectType());
        payload.put("subjectRef", tombstone.getSubjectRef());
        payload.put("artifactId", tombstone.getRemovedArtifactId());
        payload.put("removedAt", tombstone.getRemovedAt() != null ? tombstone.getRemovedAt().toString() : null);

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_TOMBSTONE_REMOVED,
                "DeletionTombstone",
                tombstone.getTombstoneId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_TOMBSTONE_REMOVED,
                "DeletionTombstone",
                tombstone.getTombstoneId(),
                payload,
                "tombstone-removed:" + tombstone.getTombstoneId()
        );
    }

    private TombstoneResponse toResponse(DeletionTombstone tombstone) {
        TombstoneResponse response = new TombstoneResponse();
        response.setTombstoneId(tombstone.getTombstoneId());
        response.setTenantId(tombstone.getTenantId());
        response.setSubjectType(tombstone.getSubjectType());
        response.setSubjectRef(tombstone.getSubjectRef());
        response.setStatus(tombstone.getTombstoneStatus().name());
        response.setReason(tombstone.getReason());
        response.setCreatedFromDeletionId(tombstone.getCreatedFromDeletionId());
        response.setCreatedArtifactId(tombstone.getCreatedArtifactId());
        response.setRemovedArtifactId(tombstone.getRemovedArtifactId());
        response.setRemovedAt(tombstone.getRemovedAt());
        return response;
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }
}