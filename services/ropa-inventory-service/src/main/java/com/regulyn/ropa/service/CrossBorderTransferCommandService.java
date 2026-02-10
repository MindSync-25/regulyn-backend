package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.CrossBorderTransferNaturalKey;
import com.regulyn.ropa.api.dto.CrossBorderTransferUpsertRequest;
import com.regulyn.ropa.api.dto.CrossBorderTransferUpsertResponse;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CrossBorderTransferCommandService {

    private static final Logger log = LoggerFactory.getLogger(CrossBorderTransferCommandService.class);

    private final CrossBorderTransferRepository transferRepository;
    private final CrossBorderTransferDataCategoryRepository dataCategoryRepository;
    private final CrossBorderTransferPurposeVersionRepository purposeVersionRepository;
    private final RopaSystemRepository systemRepository;
    private final RopaActivityVersionRepository activityVersionRepository;
    private final RopaDataCategoryRepository ropaDataCategoryRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public CrossBorderTransferCommandService(
            CrossBorderTransferRepository transferRepository,
            CrossBorderTransferDataCategoryRepository dataCategoryRepository,
            CrossBorderTransferPurposeVersionRepository purposeVersionRepository,
            RopaSystemRepository systemRepository,
            RopaActivityVersionRepository activityVersionRepository,
            RopaDataCategoryRepository ropaDataCategoryRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.dataCategoryRepository = dataCategoryRepository;
        this.purposeVersionRepository = purposeVersionRepository;
        this.systemRepository = systemRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.ropaDataCategoryRepository = ropaDataCategoryRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CrossBorderTransferUpsertResponse upsertTransfer(UUID tenantId, UUID userId, String idempotencyKey,
                                                            CrossBorderTransferUpsertRequest request) {
        validateRequest(request);

        RopaSystem system = systemRepository.findById(request.getSystemId())
            .filter(s -> tenantId.equals(s.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System not found"));

        boolean activityExists = !activityVersionRepository.findByTenantIdAndActivityId(tenantId, request.getActivityId()).isEmpty();
        if (!activityExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        }

        List<UUID> dataCategoryIds = listOrEmpty(request.getDataCategoryIds());
        if (!dataCategoryIds.isEmpty()) {
            int existing = ropaDataCategoryRepository.findByTenantIdAndDataCategoryIdIn(tenantId, dataCategoryIds).size();
            if (existing != dataCategoryIds.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Data category not found");
            }
        }

        Optional<CrossBorderTransferEntity> idemMatch = transferRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (idemMatch.isPresent() && !matchesNaturalKey(idemMatch.get(), request)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_ALREADY_USED");
        }

        CrossBorderTransferEntity entity = transferRepository
            .findByTenantIdAndActivityIdAndVendorIdAndSourceRegionAndDestinationRegionAndTransferMechanism(
                tenantId,
                request.getActivityId(),
                request.getVendorId(),
                request.getSourceRegion(),
                request.getDestinationRegion(),
                request.getTransferMechanism())
            .orElse(null);

        boolean created = false;
        if (entity == null) {
            entity = new CrossBorderTransferEntity();
            entity.setTenantId(tenantId);
            entity.setActivityId(request.getActivityId());
            entity.setVendorId(request.getVendorId());
            entity.setSourceRegion(request.getSourceRegion());
            entity.setDestinationRegion(request.getDestinationRegion());
            entity.setTransferMechanism(request.getTransferMechanism());
            created = true;
        }

        String payloadHash = computePayloadHash(request);

        if (!created && Objects.equals(entity.getIdempotencyKey(), idempotencyKey)) {
            if (!payloadMatches(entity, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            return buildResponse(entity, created, dataCategoryIds, listOrEmpty(request.getPurposeVersionIds()));
        }

        entity.setSystemId(system.getSystemId());
        entity.setLegalBasis(request.getLegalBasis());
        entity.setFrequency(request.getFrequency());
        entity.setStartedAt(request.getStartedAt());
        entity.setEndedAt(request.getEndedAt());
        entity.setIdempotencyKey(idempotencyKey);

        CrossBorderTransferEntity saved = transferRepository.save(entity);

        replaceJoinRows(tenantId, saved.getId(), dataCategoryIds, listOrEmpty(request.getPurposeVersionIds()));

        String action = created ? "CROSS_BORDER_TRANSFER_REGISTERED" : "CROSS_BORDER_TRANSFER_UPDATED";
        String eventType = created ? "ropa.cross_border_transfer_registered" : "ropa.cross_border_transfer_updated";

        writeAudit(tenantId, userId, action, saved.getId(), payloadHash, buildMetadata(saved, idempotencyKey, payloadHash,
            dataCategoryIds.size(), listOrEmpty(request.getPurposeVersionIds()).size()));

        writeOutbox(eventType, saved.getId().toString(), idempotencyKey, buildOutboxPayload(tenantId, userId, saved,
            idempotencyKey, payloadHash, dataCategoryIds.size(), listOrEmpty(request.getPurposeVersionIds()).size()));

        return buildResponse(saved, created, dataCategoryIds, listOrEmpty(request.getPurposeVersionIds()));
    }

    private void validateRequest(CrossBorderTransferUpsertRequest request) {
        if (request == null
            || request.getSystemId() == null
            || request.getActivityId() == null
            || request.getVendorId() == null
            || request.getTransferMechanism() == null
            || request.getFrequency() == null
            || request.getStartedAt() == null
            || request.getSourceRegion() == null
            || request.getDestinationRegion() == null
            || request.getLegalBasis() == null
            || request.getLegalBasis().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        if (request.getEndedAt() != null && request.getEndedAt().isBefore(request.getStartedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endedAt must be after startedAt");
        }
    }

    private boolean matchesNaturalKey(CrossBorderTransferEntity entity, CrossBorderTransferUpsertRequest request) {
        return Objects.equals(entity.getActivityId(), request.getActivityId())
            && Objects.equals(entity.getVendorId(), request.getVendorId())
            && Objects.equals(entity.getSourceRegion(), request.getSourceRegion())
            && Objects.equals(entity.getDestinationRegion(), request.getDestinationRegion())
            && entity.getTransferMechanism() == request.getTransferMechanism();
    }

    private boolean payloadMatches(CrossBorderTransferEntity entity, CrossBorderTransferUpsertRequest request) {
        List<UUID> existingCategories = dataCategoryRepository.findByTenantIdAndIdTransferId(entity.getTenantId(), entity.getId())
            .stream()
            .map(row -> row.getId().getDataCategoryId())
            .sorted()
            .toList();
        List<UUID> existingPurposes = purposeVersionRepository.findByTenantIdAndIdTransferId(entity.getTenantId(), entity.getId())
            .stream()
            .map(row -> row.getId().getPurposeVersionId())
            .sorted()
            .toList();

        List<UUID> requestedCategories = listOrEmpty(request.getDataCategoryIds()).stream().sorted().toList();
        List<UUID> requestedPurposes = listOrEmpty(request.getPurposeVersionIds()).stream().sorted().toList();

        return Objects.equals(entity.getSystemId(), request.getSystemId())
            && Objects.equals(entity.getLegalBasis(), request.getLegalBasis())
            && entity.getFrequency() == request.getFrequency()
            && Objects.equals(entity.getStartedAt(), request.getStartedAt())
            && Objects.equals(entity.getEndedAt(), request.getEndedAt())
            && existingCategories.equals(requestedCategories)
            && existingPurposes.equals(requestedPurposes);
    }

    private void replaceJoinRows(UUID tenantId, UUID transferId, List<UUID> dataCategoryIds, List<UUID> purposeVersionIds) {
        dataCategoryRepository.deleteByTenantIdAndIdTransferId(tenantId, transferId);
        purposeVersionRepository.deleteByTenantIdAndIdTransferId(tenantId, transferId);

        if (!dataCategoryIds.isEmpty()) {
            List<CrossBorderTransferDataCategoryEntity> dataCategoryEntities = dataCategoryIds.stream()
                .distinct()
                .map(id -> {
                    CrossBorderTransferDataCategoryEntity entity = new CrossBorderTransferDataCategoryEntity();
                    entity.setTenantId(tenantId);
                    entity.setId(new CrossBorderTransferDataCategoryId(transferId, id));
                    return entity;
                })
                .collect(Collectors.toList());
            dataCategoryRepository.saveAll(dataCategoryEntities);
        }

        if (!purposeVersionIds.isEmpty()) {
            List<CrossBorderTransferPurposeVersionEntity> purposeEntities = purposeVersionIds.stream()
                .distinct()
                .map(id -> {
                    CrossBorderTransferPurposeVersionEntity entity = new CrossBorderTransferPurposeVersionEntity();
                    entity.setTenantId(tenantId);
                    entity.setId(new CrossBorderTransferPurposeVersionId(transferId, id));
                    return entity;
                })
                .collect(Collectors.toList());
            purposeVersionRepository.saveAll(purposeEntities);
        }
    }

    private CrossBorderTransferUpsertResponse buildResponse(CrossBorderTransferEntity entity, boolean created,
                                                           List<UUID> dataCategoryIds, List<UUID> purposeVersionIds) {
        CrossBorderTransferUpsertResponse response = new CrossBorderTransferUpsertResponse();
        response.setTransferId(entity.getId());
        response.setCreated(created);
        response.setNaturalKey(buildNaturalKey(entity));
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setDataCategoryIds(dataCategoryIds);
        response.setPurposeVersionIds(purposeVersionIds);
        return response;
    }

    private CrossBorderTransferNaturalKey buildNaturalKey(CrossBorderTransferEntity entity) {
        CrossBorderTransferNaturalKey key = new CrossBorderTransferNaturalKey();
        key.setActivityId(entity.getActivityId());
        key.setVendorId(entity.getVendorId());
        key.setSourceRegion(entity.getSourceRegion());
        key.setDestinationRegion(entity.getDestinationRegion());
        key.setTransferMechanism(entity.getTransferMechanism());
        return key;
    }

    private List<UUID> listOrEmpty(List<UUID> values) {
        return values == null ? List.of() : values;
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, UUID transferId, String payloadHash, ObjectNode metadata) {
        try {
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType("ROPA_CROSS_BORDER_TRANSFER")
                .entityId(transferId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            log.error("Failed to write audit event: {}", e.getMessage());
        }
    }

    private void writeOutbox(String eventType, String entityId, String idempotencyKey, Map<String, Object> payload) {
        try {
            EventEnvelopeV1 event = EventFactory.create(eventType, "ropa-inventory-service", "ropa_cross_border_transfer",
                entityId, payload, idempotencyKey);
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private ObjectNode buildMetadata(CrossBorderTransferEntity entity, String idempotencyKey, String payloadHash,
                                     int dataCategoryCount, int purposeVersionCount) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("transferId", entity.getId().toString());
        node.put("systemId", entity.getSystemId().toString());
        node.put("activityId", entity.getActivityId().toString());
        node.put("vendorId", entity.getVendorId().toString());
        node.put("sourceRegion", entity.getSourceRegion());
        node.put("destinationRegion", entity.getDestinationRegion());
        node.put("transferMechanism", entity.getTransferMechanism().name());
        node.put("legalBasis", entity.getLegalBasis());
        node.put("frequency", entity.getFrequency().name());
        node.put("startedAt", entity.getStartedAt().toString());
        if (entity.getEndedAt() != null) {
            node.put("endedAt", entity.getEndedAt().toString());
        }
        node.put("dataCategoryCount", dataCategoryCount);
        node.put("purposeVersionCount", purposeVersionCount);
        node.put("payloadHash", payloadHash);
        node.put("idempotencyKey", idempotencyKey);
        return node;
    }

    private Map<String, Object> buildOutboxPayload(UUID tenantId, UUID userId, CrossBorderTransferEntity entity,
                                                   String idempotencyKey, String payloadHash, int dataCategoryCount,
                                                   int purposeVersionCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("actorUserId", userId != null ? userId.toString() : null);
        payload.put("transferId", entity.getId().toString());
        payload.put("systemId", entity.getSystemId().toString());
        payload.put("activityId", entity.getActivityId().toString());
        payload.put("vendorId", entity.getVendorId().toString());
        payload.put("sourceRegion", entity.getSourceRegion());
        payload.put("destinationRegion", entity.getDestinationRegion());
        payload.put("transferMechanism", entity.getTransferMechanism().name());
        payload.put("legalBasis", entity.getLegalBasis());
        payload.put("frequency", entity.getFrequency().name());
        payload.put("startedAt", entity.getStartedAt().toString());
        if (entity.getEndedAt() != null) {
            payload.put("endedAt", entity.getEndedAt().toString());
        }
        payload.put("dataCategoryCount", dataCategoryCount);
        payload.put("purposeVersionCount", purposeVersionCount);
        payload.put("payloadHash", payloadHash);
        payload.put("idempotencyKey", idempotencyKey);
        return payload;
    }

    private String computePayloadHash(CrossBorderTransferUpsertRequest request) {
        List<String> dataCategoryIds = listOrEmpty(request.getDataCategoryIds()).stream()
            .map(UUID::toString)
            .sorted()
            .toList();
        List<String> purposeVersionIds = listOrEmpty(request.getPurposeVersionIds()).stream()
            .map(UUID::toString)
            .sorted()
            .toList();

        String canonical = String.join("|",
            request.getSystemId().toString(),
            request.getActivityId().toString(),
            request.getVendorId().toString(),
            request.getSourceRegion(),
            request.getDestinationRegion(),
            request.getTransferMechanism().name(),
            request.getLegalBasis(),
            request.getFrequency().name(),
            request.getStartedAt().toString(),
            request.getEndedAt() != null ? request.getEndedAt().toString() : "",
            String.join(",", dataCategoryIds),
            String.join(",", purposeVersionIds)
        );
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }
}
