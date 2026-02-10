package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.RetentionPolicyUpsertRequest;
import com.regulyn.ropa.api.dto.RetentionPolicyUpsertResponse;
import com.regulyn.ropa.api.dto.RetentionScope;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RetentionPolicyCommandService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyCommandService.class);

    private final RetentionPolicySystemRepository systemPolicyRepository;
    private final RetentionPolicyActivityRepository activityPolicyRepository;
    private final RetentionPolicyCategoryPurposeRepository categoryPurposeRepository;
    private final RopaSystemRepository systemRepository;
    private final RopaActivityVersionRepository activityVersionRepository;
    private final RopaDataCategoryRepository dataCategoryRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public RetentionPolicyCommandService(
            RetentionPolicySystemRepository systemPolicyRepository,
            RetentionPolicyActivityRepository activityPolicyRepository,
            RetentionPolicyCategoryPurposeRepository categoryPurposeRepository,
            RopaSystemRepository systemRepository,
            RopaActivityVersionRepository activityVersionRepository,
            RopaDataCategoryRepository dataCategoryRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.systemPolicyRepository = systemPolicyRepository;
        this.activityPolicyRepository = activityPolicyRepository;
        this.categoryPurposeRepository = categoryPurposeRepository;
        this.systemRepository = systemRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.dataCategoryRepository = dataCategoryRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RetentionPolicyUpsertResponse upsertSystemPolicy(
            UUID tenantId,
            UUID userId,
            String idempotencyKey,
            UUID systemId,
            RetentionPolicyUpsertRequest request) {

        RopaSystem system = systemRepository.findById(systemId)
            .filter(s -> tenantId.equals(s.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System not found"));

        Optional<RetentionPolicySystemEntity> idemMatch = systemPolicyRepository
            .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (idemMatch.isPresent() && !systemId.equals(idemMatch.get().getSystemId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_ALREADY_USED");
        }

        RetentionPolicySystemEntity entity = systemPolicyRepository
            .findByTenantIdAndSystemId(tenantId, systemId)
            .orElse(null);

        boolean created = false;
        if (entity == null) {
            entity = new RetentionPolicySystemEntity();
            entity.setTenantId(tenantId);
            entity.setSystemId(system.getSystemId());
            created = true;
        }

        String payloadHash = computePayloadHash(request);

        if (!created && Objects.equals(entity.getIdempotencyKey(), idempotencyKey)) {
            if (!payloadMatches(entity, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            return toResponse(entity, RetentionScope.SYSTEM, created);
        }

        entity.setRetentionDays(request.getRetentionDays());
        entity.setRetentionBasis(request.getRetentionBasis());
        entity.setRetentionNote(request.getRetentionNote());
        entity.setReviewRequired(Boolean.TRUE.equals(request.getReviewRequired()));
        entity.setIdempotencyKey(idempotencyKey);

        RetentionPolicySystemEntity saved = systemPolicyRepository.save(entity);

        String action = created ? "RETENTION_POLICY_CREATED" : "RETENTION_POLICY_UPDATED";
        String eventType = created ? "ropa.retention_policy_created" : "ropa.retention_policy_updated";

        writeAudit(tenantId, userId, action, "ROPA_RETENTION_POLICY", saved.getId(), payloadHash, buildMetadata(
            RetentionScope.SYSTEM, saved.getSystemId(), null, null, null, request, idempotencyKey));

        writeOutbox(eventType, "ropa_retention_policy", saved.getId().toString(), idempotencyKey, buildOutboxPayload(
            tenantId, userId, RetentionScope.SYSTEM, saved.getSystemId(), null, null, null, request, idempotencyKey, payloadHash));

        return toResponse(saved, RetentionScope.SYSTEM, created);
    }

    @Transactional
    public RetentionPolicyUpsertResponse upsertActivityPolicy(
            UUID tenantId,
            UUID userId,
            String idempotencyKey,
            UUID activityId,
            RetentionPolicyUpsertRequest request) {

        boolean activityExists = !activityVersionRepository.findByTenantIdAndActivityId(tenantId, activityId).isEmpty();
        if (!activityExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        }

        Optional<RetentionPolicyActivityEntity> idemMatch = activityPolicyRepository
            .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (idemMatch.isPresent() && !activityId.equals(idemMatch.get().getActivityId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_ALREADY_USED");
        }

        RetentionPolicyActivityEntity entity = activityPolicyRepository
            .findByTenantIdAndActivityId(tenantId, activityId)
            .orElse(null);

        boolean created = false;
        if (entity == null) {
            entity = new RetentionPolicyActivityEntity();
            entity.setTenantId(tenantId);
            entity.setActivityId(activityId);
            created = true;
        }

        String payloadHash = computePayloadHash(request);

        if (!created && Objects.equals(entity.getIdempotencyKey(), idempotencyKey)) {
            if (!payloadMatches(entity, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            return toResponse(entity, RetentionScope.ACTIVITY, created);
        }

        entity.setRetentionDays(request.getRetentionDays());
        entity.setRetentionBasis(request.getRetentionBasis());
        entity.setRetentionNote(request.getRetentionNote());
        entity.setReviewRequired(Boolean.TRUE.equals(request.getReviewRequired()));
        entity.setIdempotencyKey(idempotencyKey);

        RetentionPolicyActivityEntity saved = activityPolicyRepository.save(entity);

        String action = created ? "RETENTION_POLICY_CREATED" : "RETENTION_POLICY_UPDATED";
        String eventType = created ? "ropa.retention_policy_created" : "ropa.retention_policy_updated";

        writeAudit(tenantId, userId, action, "ROPA_RETENTION_POLICY", saved.getId(), payloadHash, buildMetadata(
            RetentionScope.ACTIVITY, null, saved.getActivityId(), null, null, request, idempotencyKey));

        writeOutbox(eventType, "ropa_retention_policy", saved.getId().toString(), idempotencyKey, buildOutboxPayload(
            tenantId, userId, RetentionScope.ACTIVITY, null, saved.getActivityId(), null, null, request, idempotencyKey, payloadHash));

        return toResponse(saved, RetentionScope.ACTIVITY, created);
    }

    @Transactional
    public RetentionPolicyUpsertResponse upsertCategoryPurposePolicy(
            UUID tenantId,
            UUID userId,
            String idempotencyKey,
            UUID dataCategoryId,
            UUID purposeVersionId,
            RetentionPolicyUpsertRequest request) {

        RopaDataCategory category = dataCategoryRepository.findById(dataCategoryId)
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Data category not found"));

        Optional<RetentionPolicyCategoryPurposeEntity> idemMatch = categoryPurposeRepository
            .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (idemMatch.isPresent() && (!dataCategoryId.equals(idemMatch.get().getDataCategoryId())
            || !purposeVersionId.equals(idemMatch.get().getPurposeVersionId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_ALREADY_USED");
        }

        RetentionPolicyCategoryPurposeEntity entity = categoryPurposeRepository
            .findByTenantIdAndDataCategoryIdAndPurposeVersionId(tenantId, dataCategoryId, purposeVersionId)
            .orElse(null);

        boolean created = false;
        if (entity == null) {
            entity = new RetentionPolicyCategoryPurposeEntity();
            entity.setTenantId(tenantId);
            entity.setDataCategoryId(category.getDataCategoryId());
            entity.setPurposeVersionId(purposeVersionId);
            created = true;
        }

        String payloadHash = computePayloadHash(request);

        if (!created && Objects.equals(entity.getIdempotencyKey(), idempotencyKey)) {
            if (!payloadMatches(entity, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            return toResponse(entity, RetentionScope.CATEGORY_PURPOSE, created);
        }

        entity.setRetentionDays(request.getRetentionDays());
        entity.setRetentionBasis(request.getRetentionBasis());
        entity.setRetentionNote(request.getRetentionNote());
        entity.setReviewRequired(Boolean.TRUE.equals(request.getReviewRequired()));
        entity.setIdempotencyKey(idempotencyKey);

        RetentionPolicyCategoryPurposeEntity saved = categoryPurposeRepository.save(entity);

        String action = created ? "RETENTION_POLICY_CREATED" : "RETENTION_POLICY_UPDATED";
        String eventType = created ? "ropa.retention_policy_created" : "ropa.retention_policy_updated";

        writeAudit(tenantId, userId, action, "ROPA_RETENTION_POLICY", saved.getId(), payloadHash, buildMetadata(
            RetentionScope.CATEGORY_PURPOSE, null, null, saved.getDataCategoryId(), saved.getPurposeVersionId(), request, idempotencyKey));

        writeOutbox(eventType, "ropa_retention_policy", saved.getId().toString(), idempotencyKey, buildOutboxPayload(
            tenantId, userId, RetentionScope.CATEGORY_PURPOSE, null, null, saved.getDataCategoryId(), saved.getPurposeVersionId(), request, idempotencyKey, payloadHash));

        return toResponse(saved, RetentionScope.CATEGORY_PURPOSE, created);
    }

    private RetentionPolicyUpsertResponse toResponse(RetentionPolicySystemEntity entity, RetentionScope scope, boolean created) {
        RetentionPolicyUpsertResponse response = new RetentionPolicyUpsertResponse();
        response.setScope(scope);
        response.setSystemId(entity.getSystemId());
        response.setRetentionDays(entity.getRetentionDays());
        response.setRetentionBasis(entity.getRetentionBasis());
        response.setRetentionNote(entity.getRetentionNote());
        response.setReviewRequired(entity.getReviewRequired());
        response.setCreated(created);
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private RetentionPolicyUpsertResponse toResponse(RetentionPolicyActivityEntity entity, RetentionScope scope, boolean created) {
        RetentionPolicyUpsertResponse response = new RetentionPolicyUpsertResponse();
        response.setScope(scope);
        response.setActivityId(entity.getActivityId());
        response.setRetentionDays(entity.getRetentionDays());
        response.setRetentionBasis(entity.getRetentionBasis());
        response.setRetentionNote(entity.getRetentionNote());
        response.setReviewRequired(entity.getReviewRequired());
        response.setCreated(created);
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private RetentionPolicyUpsertResponse toResponse(RetentionPolicyCategoryPurposeEntity entity, RetentionScope scope, boolean created) {
        RetentionPolicyUpsertResponse response = new RetentionPolicyUpsertResponse();
        response.setScope(scope);
        response.setDataCategoryId(entity.getDataCategoryId());
        response.setPurposeVersionId(entity.getPurposeVersionId());
        response.setRetentionDays(entity.getRetentionDays());
        response.setRetentionBasis(entity.getRetentionBasis());
        response.setRetentionNote(entity.getRetentionNote());
        response.setReviewRequired(entity.getReviewRequired());
        response.setCreated(created);
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private boolean payloadMatches(RetentionPolicySystemEntity entity, RetentionPolicyUpsertRequest request) {
        return Objects.equals(entity.getRetentionDays(), request.getRetentionDays())
            && entity.getRetentionBasis() == request.getRetentionBasis()
            && Objects.equals(entity.getRetentionNote(), request.getRetentionNote())
            && Objects.equals(entity.getReviewRequired(), Boolean.TRUE.equals(request.getReviewRequired()));
    }

    private boolean payloadMatches(RetentionPolicyActivityEntity entity, RetentionPolicyUpsertRequest request) {
        return Objects.equals(entity.getRetentionDays(), request.getRetentionDays())
            && entity.getRetentionBasis() == request.getRetentionBasis()
            && Objects.equals(entity.getRetentionNote(), request.getRetentionNote())
            && Objects.equals(entity.getReviewRequired(), Boolean.TRUE.equals(request.getReviewRequired()));
    }

    private boolean payloadMatches(RetentionPolicyCategoryPurposeEntity entity, RetentionPolicyUpsertRequest request) {
        return Objects.equals(entity.getRetentionDays(), request.getRetentionDays())
            && entity.getRetentionBasis() == request.getRetentionBasis()
            && Objects.equals(entity.getRetentionNote(), request.getRetentionNote())
            && Objects.equals(entity.getReviewRequired(), Boolean.TRUE.equals(request.getReviewRequired()));
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, String entityType, UUID entityId, String payloadHash, ObjectNode metadata) {
        try {
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            log.error("Failed to write audit event: {}", e.getMessage());
        }
    }

    private void writeOutbox(String eventType, String entityType, String entityId, String idempotencyKey, Map<String, Object> payload) {
        try {
            EventEnvelopeV1 event = EventFactory.create(eventType, "ropa-inventory-service", entityType, entityId, payload, idempotencyKey);
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private ObjectNode buildMetadata(RetentionScope scope, UUID systemId, UUID activityId, UUID dataCategoryId, UUID purposeVersionId,
                                     RetentionPolicyUpsertRequest request, String idempotencyKey) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("scope", scope.name());
        if (systemId != null) {
            node.put("systemId", systemId.toString());
        }
        if (activityId != null) {
            node.put("activityId", activityId.toString());
        }
        if (dataCategoryId != null) {
            node.put("dataCategoryId", dataCategoryId.toString());
        }
        if (purposeVersionId != null) {
            node.put("purposeVersionId", purposeVersionId.toString());
        }
        node.put("retentionDays", request.getRetentionDays());
        node.put("retentionBasis", request.getRetentionBasis().name());
        node.put("reviewRequired", Boolean.TRUE.equals(request.getReviewRequired()));
        if (request.getRetentionNote() != null) {
            node.put("retentionNote", request.getRetentionNote());
        }
        node.put("idempotencyKey", idempotencyKey);
        return node;
    }

    private Map<String, Object> buildOutboxPayload(UUID tenantId, UUID userId, RetentionScope scope, UUID systemId, UUID activityId,
                                                   UUID dataCategoryId, UUID purposeVersionId, RetentionPolicyUpsertRequest request,
                                                   String idempotencyKey, String payloadHash) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        if (userId != null) {
            payload.put("actorUserId", userId.toString());
        }
        payload.put("scope", scope.name());
        if (systemId != null) {
            payload.put("systemId", systemId.toString());
        }
        if (activityId != null) {
            payload.put("activityId", activityId.toString());
        }
        if (dataCategoryId != null) {
            payload.put("dataCategoryId", dataCategoryId.toString());
        }
        if (purposeVersionId != null) {
            payload.put("purposeVersionId", purposeVersionId.toString());
        }
        payload.put("retentionDays", request.getRetentionDays());
        payload.put("retentionBasis", request.getRetentionBasis().name());
        if (request.getRetentionNote() != null) {
            payload.put("retentionNote", request.getRetentionNote());
        }
        payload.put("reviewRequired", Boolean.TRUE.equals(request.getReviewRequired()));
        payload.put("payloadHash", payloadHash);
        payload.put("idempotencyKey", idempotencyKey);
        return payload;
    }

    private String computePayloadHash(RetentionPolicyUpsertRequest request) {
        String canonical = request.getRetentionDays() + "|" + request.getRetentionBasis() + "|"
            + (request.getRetentionNote() == null ? "" : request.getRetentionNote()) + "|"
            + Boolean.TRUE.equals(request.getReviewRequired());
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
