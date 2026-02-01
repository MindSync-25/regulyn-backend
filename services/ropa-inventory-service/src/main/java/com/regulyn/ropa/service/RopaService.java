package com.regulyn.ropa.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RopaService {

    private static final Logger log = LoggerFactory.getLogger(RopaService.class);

    private final RopaSystemRepository systemRepository;
    private final RopaDataCategoryRepository dataCategoryRepository;
    private final RopaActivityVersionRepository activityVersionRepository;
    private final RopaActivityLinkRepository activityLinkRepository;
    private final RopaActivitySystemRepository activitySystemRepository;
    private final RopaActivityDataCategoryRepository activityDataCategoryRepository;
    private final RopaActivityVendorRepository activityVendorRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public RopaService(RopaSystemRepository systemRepository,
                       RopaDataCategoryRepository dataCategoryRepository,
                       RopaActivityVersionRepository activityVersionRepository,
                       RopaActivityLinkRepository activityLinkRepository,
                       RopaActivitySystemRepository activitySystemRepository,
                       RopaActivityDataCategoryRepository activityDataCategoryRepository,
                       RopaActivityVendorRepository activityVendorRepository,
                       AuditWriter auditWriter,
                       OutboxWriter outboxWriter) {
        this.systemRepository = systemRepository;
        this.dataCategoryRepository = dataCategoryRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.activityLinkRepository = activityLinkRepository;
        this.activitySystemRepository = activitySystemRepository;
        this.activityDataCategoryRepository = activityDataCategoryRepository;
        this.activityVendorRepository = activityVendorRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    // ========== SYSTEMS ==========

    @Transactional
    public SystemResponse createSystem(CreateSystemRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName(request.getSystemName());
        system.setSystemType(request.getSystemType());
        system.setOwnerTeam(request.getOwnerTeam());
        system.setLocation(request.getLocation());
        system.setCriticality(request.getCriticality());
        system.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());

        system = systemRepository.save(system);

        // Audit and Outbox
        writeAudit(tenantId, "SYSTEM_CREATED", "ROPA_SYSTEM", system.getSystemId(), "System created: " + system.getSystemName());
        writeOutboxEvent(tenantId, "ropa.system_created", Map.of(
            "systemId", system.getSystemId(),
            "systemName", system.getSystemName(),
            "systemType", system.getSystemType().name()
        ));

        return new SystemResponse(system.getSystemId());
    }

    @Transactional(readOnly = true)
    public List<RopaSystem> listSystems(RopaSystem.SystemType type, RopaSystem.Criticality criticality, String q) {
        UUID tenantId = TenantContextHolder.getTenantId();

        if (type != null && criticality != null) {
            return systemRepository.findAll((Specification<RopaSystem>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.equal(root.get("systemType"), type),
                    cb.equal(root.get("criticality"), criticality)
                ));
        } else if (type != null) {
            return systemRepository.findByTenantIdAndSystemType(tenantId, type);
        } else if (criticality != null) {
            return systemRepository.findByTenantIdAndCriticality(tenantId, criticality);
        } else if (q != null && !q.isBlank()) {
            return systemRepository.findAll((Specification<RopaSystem>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.like(cb.lower(root.get("systemName")), "%" + q.toLowerCase() + "%")
                ));
        } else {
            return systemRepository.findByTenantId(tenantId);
        }
    }

    @Transactional
    public SystemResponse disableSystem(UUID systemId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RopaSystem system = systemRepository.findById(systemId)
            .orElseThrow(() -> new IllegalArgumentException("System not found: " + systemId));

        if (!system.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("System does not belong to tenant");
        }

        system.setEnabled(false);
        system = systemRepository.save(system);

        // Audit and Outbox
        writeAudit(tenantId, "SYSTEM_DISABLED", "ROPA_SYSTEM", systemId, "System disabled");
        writeOutboxEvent(tenantId, "ropa.system_disabled", Map.of(
            "systemId", systemId
        ));

        return new SystemResponse(system.getSystemId(), system.getEnabled());
    }

    // ========== DATA CATEGORIES ==========

    @Transactional
    public DataCategoryResponse createDataCategory(CreateDataCategoryRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RopaDataCategory category = new RopaDataCategory();
        category.setTenantId(tenantId);
        category.setCategoryKey(request.getCategoryKey());
        category.setLabel(request.getLabel());
        category.setSensitive(request.getSensitive());
        category.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());

        category = dataCategoryRepository.save(category);

        // Audit and Outbox
        writeAudit(tenantId, "DATA_CATEGORY_CREATED", "ROPA_DATA_CATEGORY", category.getDataCategoryId(), "Data category created: " + category.getLabel());
        writeOutboxEvent(tenantId, "ropa.data_category_created", Map.of(
            "dataCategoryId", category.getDataCategoryId(),
            "categoryKey", category.getCategoryKey().name(),
            "label", category.getLabel()
        ));

        return new DataCategoryResponse(category.getDataCategoryId());
    }

    @Transactional(readOnly = true)
    public List<RopaDataCategory> listDataCategories() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return dataCategoryRepository.findByTenantId(tenantId);
    }

    // ========== PROCESSING ACTIVITIES ==========

    @Transactional
    public ActivityResponse createActivity(CreateActivityRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID activityId = UUID.randomUUID();

        RopaActivityVersion version = new RopaActivityVersion();
        version.setTenantId(tenantId);
        version.setActivityId(activityId);
        version.setVersionNumber(1);
        version.setStatus(RopaActivityVersion.Status.DRAFT);
        version.setActivityName(request.getActivityName());
        version.setPurpose(request.getPurpose());
        version.setLawfulBasis(request.getLawfulBasis());
        version.setDataPrincipalType(request.getDataPrincipalType());
        version.setDescription(request.getDescription());
        version.setRetentionPolicy(request.getRetentionPolicy());
        version.setRetentionDays(request.getRetentionDays());
        version.setRiskLevel(request.getRiskLevel());
        version.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        version.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());

        version = activityVersionRepository.save(version);

        // Audit and Outbox
        writeAudit(tenantId, "ACTIVITY_CREATED", "ROPA_ACTIVITY", activityId, "Activity created: " + version.getActivityName());
        writeOutboxEvent(tenantId, "ropa.activity_created", Map.of(
            "activityId", activityId,
            "versionId", version.getVersionId(),
            "activityName", version.getActivityName()
        ));

        return new ActivityResponse(activityId, RopaActivityVersion.Status.DRAFT);
    }

    @Transactional
    public LinkActivityResponse linkActivity(UUID activityId, LinkActivityRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Find latest DRAFT version
        RopaActivityVersion draftVersion = activityVersionRepository
            .findByTenantIdAndActivityId(tenantId, activityId).stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.DRAFT)
            .max(Comparator.comparing(RopaActivityVersion::getVersionNumber))
            .orElseThrow(() -> new IllegalArgumentException("No DRAFT version found for activity: " + activityId));

        // Clear existing links for this version
        activitySystemRepository.deleteByVersionId(draftVersion.getVersionId());
        activityDataCategoryRepository.deleteByVersionId(draftVersion.getVersionId());
        activityVendorRepository.deleteByVersionId(draftVersion.getVersionId());

        // Create new system links
        if (request.getSystemIds() != null) {
            for (UUID systemId : request.getSystemIds()) {
                RopaActivitySystem link = new RopaActivitySystem();
                link.setTenantId(tenantId);
                link.setVersionId(draftVersion.getVersionId());
                link.setSystemId(systemId);
                activitySystemRepository.save(link);
            }
        }

        // Create new data category links
        if (request.getDataCategoryIds() != null) {
            for (UUID categoryId : request.getDataCategoryIds()) {
                RopaActivityDataCategory link = new RopaActivityDataCategory();
                link.setTenantId(tenantId);
                link.setVersionId(draftVersion.getVersionId());
                link.setDataCategoryId(categoryId);
                activityDataCategoryRepository.save(link);
            }
        }

        // Create new vendor links
        if (request.getVendorIds() != null) {
            for (UUID vendorId : request.getVendorIds()) {
                RopaActivityVendor link = new RopaActivityVendor();
                link.setTenantId(tenantId);
                link.setVersionId(draftVersion.getVersionId());
                link.setVendorId(vendorId);
                activityVendorRepository.save(link);
            }
        }

        // Create link record
        RopaActivityLink linkRecord = new RopaActivityLink();
        linkRecord.setTenantId(tenantId);
        linkRecord.setActivityId(activityId);
        linkRecord.setVersionId(draftVersion.getVersionId());
        linkRecord.setNotes(request.getNotes());
        activityLinkRepository.save(linkRecord);

        // Audit and Outbox
        writeAudit(tenantId, "ACTIVITY_LINKED", "ROPA_ACTIVITY", activityId, "Activity linked with systems and categories");
        writeOutboxEvent(tenantId, "ropa.activity_linked", Map.of(
            "activityId", activityId,
            "versionId", draftVersion.getVersionId(),
            "systemCount", request.getSystemIds() != null ? request.getSystemIds().size() : 0,
            "categoryCount", request.getDataCategoryIds() != null ? request.getDataCategoryIds().size() : 0
        ));

        return new LinkActivityResponse(activityId, true);
    }

    @Transactional
    public ActivityResponse publishActivity(UUID activityId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Find latest DRAFT version
        RopaActivityVersion draftVersion = activityVersionRepository
            .findByTenantIdAndActivityId(tenantId, activityId).stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.DRAFT)
            .max(Comparator.comparing(RopaActivityVersion::getVersionNumber))
            .orElseThrow(() -> new IllegalArgumentException("No DRAFT version found for activity: " + activityId));

        // Retire existing PUBLISHED versions
        List<RopaActivityVersion> publishedVersions = activityVersionRepository
            .findByTenantIdAndActivityId(tenantId, activityId).stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.PUBLISHED)
            .collect(Collectors.toList());

        for (RopaActivityVersion published : publishedVersions) {
            published.setStatus(RopaActivityVersion.Status.RETIRED);
            activityVersionRepository.save(published);
        }

        // Publish the DRAFT
        draftVersion.setStatus(RopaActivityVersion.Status.PUBLISHED);
        draftVersion.setPublishedAt(Instant.now());
        draftVersion = activityVersionRepository.save(draftVersion);

        // Audit and Outbox
        writeAudit(tenantId, "ACTIVITY_PUBLISHED", "ROPA_ACTIVITY", activityId, "Activity published");
        writeOutboxEvent(tenantId, "ropa.activity_published", Map.of(
            "activityId", activityId,
            "versionId", draftVersion.getVersionId(),
            "versionNumber", draftVersion.getVersionNumber()
        ));

        return new ActivityResponse(activityId, RopaActivityVersion.Status.PUBLISHED, draftVersion.getPublishedAt());
    }

    @Transactional
    public CreateVersionResponse createNewVersion(UUID activityId, CreateVersionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Find latest version to determine next version number
        RopaActivityVersion latestVersion = activityVersionRepository
            .findFirstByTenantIdAndActivityIdOrderByVersionNumberDesc(tenantId, activityId)
            .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));

        int newVersionNumber = latestVersion.getVersionNumber() + 1;

        // Clone latest version as new DRAFT
        RopaActivityVersion newVersion = new RopaActivityVersion();
        newVersion.setTenantId(tenantId);
        newVersion.setActivityId(activityId);
        newVersion.setVersionNumber(newVersionNumber);
        newVersion.setStatus(RopaActivityVersion.Status.DRAFT);
        newVersion.setActivityName(latestVersion.getActivityName());
        newVersion.setPurpose(latestVersion.getPurpose());
        newVersion.setLawfulBasis(latestVersion.getLawfulBasis());
        newVersion.setDataPrincipalType(latestVersion.getDataPrincipalType());
        newVersion.setDescription(latestVersion.getDescription());
        newVersion.setRetentionPolicy(latestVersion.getRetentionPolicy());
        newVersion.setRetentionDays(latestVersion.getRetentionDays());
        newVersion.setRiskLevel(latestVersion.getRiskLevel());
        newVersion.setEnabled(latestVersion.getEnabled());
        newVersion.setMetadata(new HashMap<>(latestVersion.getMetadata()));

        if (request.getChangeSummary() != null) {
            newVersion.getMetadata().put("changeSummary", request.getChangeSummary());
        }

        newVersion = activityVersionRepository.save(newVersion);

        // Audit and Outbox
        writeAudit(tenantId, "ACTIVITY_VERSION_CREATED", "ROPA_ACTIVITY", activityId, "New version created: v" + newVersionNumber);
        writeOutboxEvent(tenantId, "ropa.activity_version_created", Map.of(
            "activityId", activityId,
            "versionId", newVersion.getVersionId(),
            "versionNumber", newVersionNumber
        ));

        return new CreateVersionResponse(newVersion.getVersionId(), newVersionNumber);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActivity(UUID activityId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Get latest published or draft version
        RopaActivityVersion version = activityVersionRepository
            .findByTenantIdAndActivityId(tenantId, activityId).stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.PUBLISHED || v.getStatus() == RopaActivityVersion.Status.DRAFT)
            .max(Comparator.comparing(RopaActivityVersion::getVersionNumber))
            .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));

        // Load links
        List<RopaActivitySystem> systems = activitySystemRepository.findByVersionId(version.getVersionId());
        List<RopaActivityDataCategory> categories = activityDataCategoryRepository.findByVersionId(version.getVersionId());
        List<RopaActivityVendor> vendors = activityVendorRepository.findByVersionId(version.getVersionId());

        Map<String, Object> response = new HashMap<>();
        response.put("activityId", version.getActivityId());
        response.put("versionId", version.getVersionId());
        response.put("versionNumber", version.getVersionNumber());
        response.put("status", version.getStatus());
        response.put("activityName", version.getActivityName());
        response.put("purpose", version.getPurpose());
        response.put("lawfulBasis", version.getLawfulBasis());
        response.put("dataPrincipalType", version.getDataPrincipalType());
        response.put("description", version.getDescription());
        response.put("retentionPolicy", version.getRetentionPolicy());
        response.put("retentionDays", version.getRetentionDays());
        response.put("riskLevel", version.getRiskLevel());
        response.put("enabled", version.getEnabled());
        response.put("publishedAt", version.getPublishedAt());
        response.put("systemIds", systems.stream().map(RopaActivitySystem::getSystemId).collect(Collectors.toList()));
        response.put("dataCategoryIds", categories.stream().map(RopaActivityDataCategory::getDataCategoryId).collect(Collectors.toList()));
        response.put("vendorIds", vendors.stream().map(RopaActivityVendor::getVendorId).collect(Collectors.toList()));

        return response;
    }

    @Transactional(readOnly = true)
    public Page<RopaActivityVersion> listActivities(
        RopaActivityVersion.Status status,
        RopaActivityVersion.RiskLevel riskLevel,
        RopaActivityVersion.LawfulBasis lawfulBasis,
        String q,
        UUID systemId,
        UUID dataCategoryId,
        int page,
        int size
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Pageable pageable = PageRequest.of(page, size);

        Specification<RopaActivityVersion> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (lawfulBasis != null) {
                predicates.add(cb.equal(root.get("lawfulBasis"), lawfulBasis));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("activityName")), "%" + q.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<RopaActivityVersion> result = activityVersionRepository.findAll(spec, pageable);

        // Filter by systemId or dataCategoryId if provided
        if (systemId != null || dataCategoryId != null) {
            List<RopaActivityVersion> filtered = result.getContent().stream()
                .filter(version -> {
                    if (systemId != null) {
                        List<RopaActivitySystem> systems = activitySystemRepository.findByVersionId(version.getVersionId());
                        if (systems.stream().noneMatch(s -> s.getSystemId().equals(systemId))) {
                            return false;
                        }
                    }
                    if (dataCategoryId != null) {
                        List<RopaActivityDataCategory> categories = activityDataCategoryRepository.findByVersionId(version.getVersionId());
                        if (categories.stream().noneMatch(c -> c.getDataCategoryId().equals(dataCategoryId))) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, result.getTotalElements());
        }

        return result;
    }

    // ========== HELPER METHODS ==========

    private void writeAudit(UUID tenantId, String action, String entityType, UUID entityId, String description) {
        try {
            String payloadHash = computeHashString(description);
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
            String entityId = payload.get("activityId") != null ? payload.get("activityId").toString() : "";
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "ropa-inventory-service",
                "ropa_activity",
                entityId,
                payload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private String computeHashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
