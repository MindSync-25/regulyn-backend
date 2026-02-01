package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.CreateRopaExportRequest;
import com.regulyn.ropa.api.dto.RopaExportResponse;
import com.regulyn.ropa.client.EvidenceClient;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RopaExportService {

    private static final Logger log = LoggerFactory.getLogger(RopaExportService.class);

    private final RopaActivityVersionRepository activityVersionRepository;
    private final RopaActivitySystemRepository activitySystemRepository;
    private final RopaActivityDataCategoryRepository activityDataCategoryRepository;
    private final RopaActivityVendorRepository activityVendorRepository;
    private final RopaSystemRepository systemRepository;
    private final RopaDataCategoryRepository dataCategoryRepository;
    private final RopaExportRepository exportRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public RopaExportService(RopaActivityVersionRepository activityVersionRepository,
                             RopaActivitySystemRepository activitySystemRepository,
                             RopaActivityDataCategoryRepository activityDataCategoryRepository,
                             RopaActivityVendorRepository activityVendorRepository,
                             RopaSystemRepository systemRepository,
                             RopaDataCategoryRepository dataCategoryRepository,
                             RopaExportRepository exportRepository,
                             EvidenceClient evidenceClient,
                             AuditWriter auditWriter,
                             OutboxWriter outboxWriter,
                             ObjectMapper objectMapper) {
        this.activityVersionRepository = activityVersionRepository;
        this.activitySystemRepository = activitySystemRepository;
        this.activityDataCategoryRepository = activityDataCategoryRepository;
        this.activityVendorRepository = activityVendorRepository;
        this.systemRepository = systemRepository;
        this.dataCategoryRepository = dataCategoryRepository;
        this.exportRepository = exportRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RopaExportResponse createRopaExport(CreateRopaExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Build snapshot
            List<Map<String, Object>> activities = buildActivitySnapshot(tenantId, request);

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("generatedAt", Instant.now().toString());
            snapshot.put("tenantId", tenantId.toString());
            snapshot.put("title", request.getTitle() != null ? request.getTitle() : "ROPA Export");
            snapshot.put("periodFrom", request.getPeriodFrom() != null ? request.getPeriodFrom().toString() : null);
            snapshot.put("periodTo", request.getPeriodTo() != null ? request.getPeriodTo().toString() : null);
            snapshot.put("activities", activities);
            snapshot.put("activityCount", activities.size());

            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            String snapshotHash = hashString(snapshotJson);

            // Create evidence
            Map<String, Object> evidencePayload = new HashMap<>();
            evidencePayload.put("type", "ROPA_SNAPSHOT");
            evidencePayload.put("generatedAt", Instant.now().toString());
            evidencePayload.put("snapshotHash", snapshotHash);
            evidencePayload.put("snapshotJson", snapshotJson);
            evidencePayload.put("metadata", Map.of(
                "activityCount", activities.size(),
                "title", snapshot.get("title")
            ));

            UUID evidenceId = evidenceClient.createEvidence(evidencePayload);

            // Create bundle
            String referenceId = "ropa:" +
                (request.getPeriodFrom() != null ? request.getPeriodFrom() : "all") + ":" +
                (request.getPeriodTo() != null ? request.getPeriodTo() : "all");

            Map<String, Object> bundlePayload = new HashMap<>();
            bundlePayload.put("bundleType", "AUDIT_EXPORT");
            bundlePayload.put("referenceType", "PERIOD");
            bundlePayload.put("referenceId", referenceId);
            bundlePayload.put("evidenceIds", List.of(evidenceId));

            UUID bundleId = evidenceClient.createBundle(bundlePayload);

            // Create export
            UUID evidenceExportId = evidenceClient.createExport(bundleId);

            // Save export record
            RopaExport export = new RopaExport();
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setEvidenceExportId(evidenceExportId);
            export = exportRepository.save(export);

            // Audit and Outbox
            writeAudit(tenantId, "ROPA_EXPORT_CREATED", "ROPA_EXPORT", export.getExportId(), "ROPA export created");
            writeOutboxEvent(tenantId, "ropa.export_created", Map.of(
                "exportId", export.getExportId(),
                "bundleId", bundleId,
                "activityCount", activities.size()
            ));

            String downloadPath = "/ropa/exports/" + export.getExportId() + "/download";

            return new RopaExportResponse(bundleId, export.getExportId(), downloadPath);

        } catch (Exception e) {
            log.error("Failed to create ROPA export", e);
            throw new RuntimeException("Evidence service unavailable or failed", e);
        }
    }

    private List<Map<String, Object>> buildActivitySnapshot(UUID tenantId, CreateRopaExportRequest request) {
        // Get published activities
        List<RopaActivityVersion> activities = activityVersionRepository.findByTenantIdAndStatus(
            tenantId, RopaActivityVersion.Status.PUBLISHED);

        // Apply filters
        if (request.getFilters() != null) {
            if (request.getFilters().getStatus() != null) {
                activities = activities.stream()
                    .filter(a -> a.getStatus() == request.getFilters().getStatus())
                    .collect(Collectors.toList());
            }
            if (request.getFilters().getRiskLevel() != null) {
                activities = activities.stream()
                    .filter(a -> a.getRiskLevel() == request.getFilters().getRiskLevel())
                    .collect(Collectors.toList());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (RopaActivityVersion activity : activities) {
            Map<String, Object> activityMap = new HashMap<>();
            activityMap.put("activityId", activity.getActivityId().toString());
            activityMap.put("versionNumber", activity.getVersionNumber());
            activityMap.put("activityName", activity.getActivityName());
            activityMap.put("purpose", activity.getPurpose());
            activityMap.put("lawfulBasis", activity.getLawfulBasis().name());
            activityMap.put("dataPrincipalType", activity.getDataPrincipalType().name());
            activityMap.put("description", activity.getDescription());
            activityMap.put("retentionPolicy", activity.getRetentionPolicy());
            activityMap.put("retentionDays", activity.getRetentionDays());
            activityMap.put("riskLevel", activity.getRiskLevel().name());
            activityMap.put("publishedAt", activity.getPublishedAt() != null ? activity.getPublishedAt().toString() : null);

            // Load systems
            List<RopaActivitySystem> systemLinks = activitySystemRepository.findByVersionId(activity.getVersionId());
            List<Map<String, Object>> systems = new ArrayList<>();
            for (RopaActivitySystem link : systemLinks) {
                systemRepository.findById(link.getSystemId()).ifPresent(system -> {
                    Map<String, Object> systemMap = new HashMap<>();
                    systemMap.put("systemId", system.getSystemId().toString());
                    systemMap.put("systemName", system.getSystemName());
                    systemMap.put("systemType", system.getSystemType().name());
                    systemMap.put("location", system.getLocation().name());
                    systems.add(systemMap);
                });
            }
            activityMap.put("systems", systems);

            // Load data categories
            List<RopaActivityDataCategory> categoryLinks = activityDataCategoryRepository.findByVersionId(activity.getVersionId());
            List<Map<String, Object>> categories = new ArrayList<>();
            for (RopaActivityDataCategory link : categoryLinks) {
                dataCategoryRepository.findById(link.getDataCategoryId()).ifPresent(category -> {
                    Map<String, Object> categoryMap = new HashMap<>();
                    categoryMap.put("dataCategoryId", category.getDataCategoryId().toString());
                    categoryMap.put("categoryKey", category.getCategoryKey().name());
                    categoryMap.put("label", category.getLabel());
                    categoryMap.put("sensitive", category.getSensitive());
                    categories.add(categoryMap);
                });
            }
            activityMap.put("dataCategories", categories);

            // Load vendors
            List<RopaActivityVendor> vendorLinks = activityVendorRepository.findByVersionId(activity.getVersionId());
            activityMap.put("vendorIds", vendorLinks.stream()
                .map(v -> v.getVendorId().toString())
                .collect(Collectors.toList()));

            result.add(activityMap);
        }

        return result;
    }

    public byte[] downloadRopaExport(UUID exportId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RopaExport export = exportRepository.findById(exportId)
            .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));

        if (!export.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Export does not belong to tenant");
        }

        try {
            return evidenceClient.downloadExport(export.getEvidenceExportId());
        } catch (Exception e) {
            log.error("Failed to download export", e);
            throw new RuntimeException("Evidence service unavailable", e);
        }
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash string", e);
        }
    }

    // ========== HELPER METHODS ==========

    private void writeAudit(UUID tenantId, String action, String entityType, UUID entityId, String description) {
        try {
            String payloadHash = hashString(description);
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .build();
            
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            log.error("Failed to write audit event: {}", e.getMessage());
        }
    }

    private void writeOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        try {
            String entityId = payload.get("exportId") != null ? payload.get("exportId").toString() : "";
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "ropa-inventory-service",
                "ropa_export",
                entityId,
                payload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
