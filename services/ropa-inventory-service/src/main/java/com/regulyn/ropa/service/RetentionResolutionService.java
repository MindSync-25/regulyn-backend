package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.model.RetentionPolicyActivityEntity;
import com.regulyn.ropa.model.RetentionPolicyCategoryPurposeEntity;
import com.regulyn.ropa.model.RetentionPolicySystemEntity;
import com.regulyn.ropa.repository.RetentionPolicyActivityRepository;
import com.regulyn.ropa.repository.RetentionPolicyCategoryPurposeRepository;
import com.regulyn.ropa.repository.RetentionPolicySystemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RetentionResolutionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionResolutionService.class);

    private final RetentionPolicyCategoryPurposeRepository categoryPurposeRepository;
    private final RetentionPolicyActivityRepository activityRepository;
    private final RetentionPolicySystemRepository systemRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public RetentionResolutionService(
            RetentionPolicyCategoryPurposeRepository categoryPurposeRepository,
            RetentionPolicyActivityRepository activityRepository,
            RetentionPolicySystemRepository systemRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.categoryPurposeRepository = categoryPurposeRepository;
        this.activityRepository = activityRepository;
        this.systemRepository = systemRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public RetentionResolveResponse resolveEffectiveRetention(
            UUID tenantId,
            UUID systemId,
            UUID activityId,
            UUID dataCategoryId,
            UUID purposeVersionId,
            boolean emitAudit) {

        RetentionResolveResponse response = new RetentionResolveResponse();
        response.setSystemId(systemId);
        response.setActivityId(activityId);
        response.setDataCategoryId(dataCategoryId);
        response.setPurposeVersionId(purposeVersionId);

        RetentionEffectiveRetention effective = new RetentionEffectiveRetention();

        Optional<RetentionPolicyCategoryPurposeEntity> categoryPolicy = Optional.empty();
        if (dataCategoryId != null && purposeVersionId != null) {
            categoryPolicy = categoryPurposeRepository
                .findByTenantIdAndDataCategoryIdAndPurposeVersionId(tenantId, dataCategoryId, purposeVersionId);
        }

        if (categoryPolicy.isPresent()) {
            RetentionPolicyCategoryPurposeEntity entity = categoryPolicy.get();
            effective.setLevel(RetentionEffectiveLevel.CATEGORY_PURPOSE);
            effective.setRetentionDays(entity.getRetentionDays());
            effective.setRetentionBasis(entity.getRetentionBasis());
            effective.setRetentionNote(entity.getRetentionNote());
            effective.setReviewRequired(entity.getReviewRequired());
        } else {
            Optional<RetentionPolicyActivityEntity> activityPolicy = activityRepository
                .findByTenantIdAndActivityId(tenantId, activityId);
            if (activityPolicy.isPresent()) {
                RetentionPolicyActivityEntity entity = activityPolicy.get();
                effective.setLevel(RetentionEffectiveLevel.ACTIVITY);
                effective.setRetentionDays(entity.getRetentionDays());
                effective.setRetentionBasis(entity.getRetentionBasis());
                effective.setRetentionNote(entity.getRetentionNote());
                effective.setReviewRequired(entity.getReviewRequired());
            } else {
                Optional<RetentionPolicySystemEntity> systemPolicy = systemRepository
                    .findByTenantIdAndSystemId(tenantId, systemId);
                if (systemPolicy.isPresent()) {
                    RetentionPolicySystemEntity entity = systemPolicy.get();
                    effective.setLevel(RetentionEffectiveLevel.SYSTEM);
                    effective.setRetentionDays(entity.getRetentionDays());
                    effective.setRetentionBasis(entity.getRetentionBasis());
                    effective.setRetentionNote(entity.getRetentionNote());
                    effective.setReviewRequired(entity.getReviewRequired());
                } else {
                    effective.setLevel(RetentionEffectiveLevel.NONE);
                }
            }
        }

        response.setEffective(effective);

        if (emitAudit) {
            writeResolutionEvents(tenantId, systemId, activityId, dataCategoryId, purposeVersionId, effective);
        }

        return response;
    }

    private void writeResolutionEvents(UUID tenantId, UUID systemId, UUID activityId, UUID dataCategoryId, UUID purposeVersionId,
                                       RetentionEffectiveRetention effective) {
        try {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("systemId", systemId.toString());
            metadata.put("activityId", activityId.toString());
            if (dataCategoryId != null) {
                metadata.put("dataCategoryId", dataCategoryId.toString());
            }
            if (purposeVersionId != null) {
                metadata.put("purposeVersionId", purposeVersionId.toString());
            }
            metadata.put("level", effective.getLevel().name());
            if (effective.getRetentionDays() != null) {
                metadata.put("retentionDays", effective.getRetentionDays());
            }
            if (effective.getRetentionBasis() != null) {
                metadata.put("retentionBasis", effective.getRetentionBasis().name());
            }
            if (effective.getRetentionNote() != null) {
                metadata.put("retentionNote", effective.getRetentionNote());
            }
            if (effective.getReviewRequired() != null) {
                metadata.put("reviewRequired", effective.getReviewRequired());
            }

            String payloadHash = sha256(metadata.toString());
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .action("RETENTION_EFFECTIVE_RESOLVED")
                .entityType("ROPA_RETENTION_RESOLUTION")
                .entityId(systemId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();
            auditWriter.write(auditEvent);

            Map<String, Object> payload = new HashMap<>();
            payload.put("tenantId", tenantId.toString());
            payload.put("systemId", systemId.toString());
            payload.put("activityId", activityId.toString());
            if (dataCategoryId != null) {
                payload.put("dataCategoryId", dataCategoryId.toString());
            }
            if (purposeVersionId != null) {
                payload.put("purposeVersionId", purposeVersionId.toString());
            }
            payload.put("level", effective.getLevel().name());
            if (effective.getRetentionDays() != null) {
                payload.put("retentionDays", effective.getRetentionDays());
            }
            if (effective.getRetentionBasis() != null) {
                payload.put("retentionBasis", effective.getRetentionBasis().name());
            }
            if (effective.getRetentionNote() != null) {
                payload.put("retentionNote", effective.getRetentionNote());
            }
            if (effective.getReviewRequired() != null) {
                payload.put("reviewRequired", effective.getReviewRequired());
            }
            payload.put("payloadHash", payloadHash);

            EventEnvelopeV1 event = EventFactory.create(
                "ropa.retention_effective_resolved",
                "ropa-inventory-service",
                "ropa_retention_resolution",
                systemId.toString(),
                payload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write retention resolution events: {}", e.getMessage());
        }
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
