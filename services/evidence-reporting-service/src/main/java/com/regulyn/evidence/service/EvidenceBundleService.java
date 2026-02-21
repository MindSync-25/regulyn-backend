package com.regulyn.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.evidence.entity.EvidenceBundle;
import com.regulyn.evidence.entity.EvidenceBundleItem;
import com.regulyn.evidence.entity.EvidenceExport;
import com.regulyn.evidence.entity.EvidenceRecord;
import com.regulyn.evidence.model.*;
import com.regulyn.evidence.repository.EvidenceBundleItemRepository;
import com.regulyn.evidence.repository.EvidenceBundleRepository;
import com.regulyn.evidence.repository.EvidenceExportRepository;
import com.regulyn.evidence.repository.EvidenceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvidenceBundleService {

    private final EvidenceBundleRepository bundleRepository;
    private final EvidenceBundleItemRepository itemRepository;
    private final EvidenceExportRepository exportRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final ExportService exportService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public EvidenceBundleService(
            EvidenceBundleRepository bundleRepository,
            EvidenceBundleItemRepository itemRepository,
            EvidenceExportRepository exportRepository,
            EvidenceRecordRepository evidenceRecordRepository,
            ExportService exportService,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.bundleRepository = bundleRepository;
        this.itemRepository = itemRepository;
        this.exportRepository = exportRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.exportService = exportService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public CreateBundleResponse createBundle(CreateBundleRequest request) {
        TenantContext ctx = TenantContextHolder.getContext();
        UUID tenantId = ctx != null && ctx.getTenantId() != null 
            ? ctx.getTenantId() 
            : UUID.fromString("ac0d62fc-b927-48e6-80ff-7d8acedfe054");
        UUID userId = ctx != null && ctx.getUserId() != null
            ? ctx.getUserId()
            : UUID.fromString("22222222-2222-2222-2222-222222222222");

        // Validate evidence IDs exist and belong to tenant
        validateEvidenceIds(tenantId, request.evidenceIds());

        // Build manifest
        Map<String, Object> manifest = buildManifest(request, tenantId);
        String canonicalManifest = canonicalJson(manifest);
        String bundleHash = computeHash(canonicalManifest);

        // Create bundle entity
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.setTenantId(tenantId);
        bundle.setBundleType(request.bundleType());
        bundle.setReferenceType(request.referenceType());
        bundle.setReferenceId(request.referenceId());
        bundle.setTitle(request.title());
        bundle.setDescription(request.description());
        bundle.setManifestJson(manifest);
        bundle.setBundleHash(bundleHash);
        bundle.setCreatedBy(userId);
        bundle = bundleRepository.save(bundle);

        // Create bundle items for evidence
        List<EvidenceBundleItem> items = new ArrayList<>();
        for (String evidenceIdStr : request.evidenceIds()) {
            EvidenceRecord evidence = evidenceRecordRepository
                    .findByEvidenceIdAndTenantId(evidenceIdStr, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceIdStr));

            EvidenceBundleItem item = new EvidenceBundleItem();
            item.setTenantId(tenantId);
            item.setBundleId(bundle.getBundleId());
            item.setItemType("EVIDENCE");
            item.setEvidenceId(UUID.fromString(evidenceIdStr));
            item.setItemHash(evidence.getEvidenceHash());
            item.setItemMeta(Map.of("evidenceType", evidence.getEvidenceType()));
            items.add(item);
        }

        // Create bundle items for artifacts (if any)
        if (request.artifactIds() != null) {
            for (String artifactIdStr : request.artifactIds()) {
                UUID artifactId = UUID.fromString(artifactIdStr);
                
                EvidenceBundleItem item = new EvidenceBundleItem();
                item.setTenantId(tenantId);
                item.setBundleId(bundle.getBundleId());
                item.setItemType("ARTIFACT");
                item.setArtifactId(artifactId);
                item.setItemHash(computeHash(artifactId.toString())); // placeholder
                items.add(item);
            }
        }

        itemRepository.saveAll(items);

        // Audit event
        auditWriter.write(AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action("evidence.bundle_created")
                .entityType("evidence_bundle")
                .entityId(bundle.getBundleId().toString())
                .payloadHash(bundleHash)
                .build());

        // Outbox event
        Map<String, Object> eventPayload = Map.of(
                "bundleId", bundle.getBundleId().toString(),
                "bundleType", request.bundleType(),
                "referenceType", request.referenceType(),
                "referenceId", request.referenceId(),
                "bundleHash", bundleHash
        );
        EventEnvelopeV1 event = EventFactory.create(
                "evidence.bundle_created",
                "evidence-reporting-service",
                "evidence_bundle",
                bundle.getBundleId().toString(),
                eventPayload
        );
        event.setTenantId(tenantId);
        outboxWriter.write(event);

        return new CreateBundleResponse(
                bundle.getBundleId(),
                bundleHash,
                bundle.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public BundleManifestResponse getBundle(UUID bundleId) {
        TenantContext ctx = TenantContextHolder.getContext();
        UUID tenantId = ctx != null && ctx.getTenantId() != null 
            ? ctx.getTenantId() 
            : UUID.fromString("ac0d62fc-b927-48e6-80ff-7d8acedfe054"); // Fallback for local dev

        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(bundleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found"));

        List<EvidenceBundleItem> items = itemRepository.findByBundleIdAndTenantId(bundleId, tenantId);

        List<BundleManifestResponse.BundleItem> bundleItems = items.stream()
                .map(item -> new BundleManifestResponse.BundleItem(
                        item.getItemId(),
                        item.getItemType(),
                        item.getEvidenceId(),
                        item.getArtifactId(),
                        item.getItemHash(),
                        item.getItemMeta()
                ))
                .collect(Collectors.toList());

        return new BundleManifestResponse(
                bundle.getBundleId(),
                bundle.getBundleType(),
                bundle.getReferenceType(),
                bundle.getReferenceId(),
                bundle.getTitle(),
                bundle.getDescription(),
                bundle.getBundleHash(),
                bundle.getStatus(),
                bundle.getCreatedAt(),
                bundle.getCreatedBy(),
                bundleItems,
                (Map<String, Object>) bundle.getManifestJson().get("metadata")
        );
    }

    @Transactional
    public ExportResponse exportBundle(UUID bundleId) {
        TenantContext ctx = TenantContextHolder.getContext();
        UUID tenantId = ctx != null && ctx.getTenantId() != null 
            ? ctx.getTenantId() 
            : UUID.fromString("ac0d62fc-b927-48e6-80ff-7d8acedfe054");
        UUID userId = ctx != null && ctx.getUserId() != null
            ? ctx.getUserId()
            : UUID.fromString("22222222-2222-2222-2222-222222222222");

        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(bundleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found"));

        try {
            // Generate export ZIP
            byte[] zipBytes = exportService.generateExportZip(bundle);
            String exportHash = computeHash(zipBytes);
            
            // Save export to filesystem
            UUID exportId = UUID.randomUUID();
            String exportPath = exportService.saveExport(tenantId, exportId, zipBytes);

            // Create export record
            EvidenceExport export = new EvidenceExport();
            export.setExportId(exportId);
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setExportPath(exportPath);
            export.setExportHash(exportHash);
            export.setCreatedBy(userId);
            export.setStatus("READY");
            exportRepository.save(export);

            // Update bundle status
            bundle.setStatus("EXPORTED");
            bundleRepository.save(bundle);

            // Audit
            auditWriter.write(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorId(userId)
                    .actorType(AuditEvent.ActorType.USER)
                    .action("evidence.bundle_exported")
                    .entityType("evidence_export")
                    .entityId(exportId.toString())
                    .payloadHash(exportHash)
                    .build());

            // Outbox
            Map<String, Object> eventPayload = Map.of(
                    "exportId", exportId.toString(),
                    "bundleId", bundleId.toString(),
                    "exportHash", exportHash
            );
            EventEnvelopeV1 event = EventFactory.create(
                    "evidence.bundle_exported",
                    "evidence-reporting-service",
                    "evidence_export",
                    exportId.toString(),
                    eventPayload
            );
            event.setTenantId(tenantId);
            outboxWriter.write(event);

            return new ExportResponse(exportId, "READY", exportPath, exportHash);

        } catch (Exception e) {
            // Record failure
            UUID exportId = UUID.randomUUID();
            EvidenceExport export = new EvidenceExport();
            export.setExportId(exportId);
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setExportPath("");
            export.setExportHash("");
            export.setCreatedBy(userId);
            export.setStatus("FAILED");
            export.setFailureReason(e.getMessage());
            exportRepository.save(export);

            // Audit failure
            auditWriter.write(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorId(userId)
                    .actorType(AuditEvent.ActorType.USER)
                    .action("evidence.export_failed")
                    .entityType("evidence_export")
                    .entityId(exportId.toString())
                    .payloadHash(computeHash(e.getMessage()))
                    .build());

            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public VerifyResponse verifyBundle(UUID bundleId) {
        TenantContext ctx = TenantContextHolder.getContext();
        UUID tenantId = ctx != null && ctx.getTenantId() != null 
            ? ctx.getTenantId() 
            : UUID.fromString("ac0d62fc-b927-48e6-80ff-7d8acedfe054");
        UUID userId = ctx != null && ctx.getUserId() != null
            ? ctx.getUserId()
            : UUID.fromString("22222222-2222-2222-2222-222222222222");

        EvidenceBundle bundle = bundleRepository.findByBundleIdAndTenantId(bundleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found"));

        List<String> problems = new ArrayList<>();

        // Verify bundle hash
        String canonicalManifest = canonicalJson(bundle.getManifestJson());
        String recomputedHash = computeHash(canonicalManifest);
        if (!recomputedHash.equals(bundle.getBundleHash())) {
            problems.add("Bundle hash mismatch: expected " + bundle.getBundleHash() + ", got " + recomputedHash);
        }

        // Verify item hashes
        List<EvidenceBundleItem> items = itemRepository.findByBundleIdAndTenantId(bundleId, tenantId);
        for (EvidenceBundleItem item : items) {
            if ("EVIDENCE".equals(item.getItemType())) {
                evidenceRecordRepository.findByEvidencePkAndTenantId(item.getEvidenceId(), tenantId)
                        .ifPresentOrElse(
                                evidence -> {
                                    if (!evidence.getEvidenceHash().equals(item.getItemHash())) {
                                        problems.add("Evidence hash mismatch for " + item.getEvidenceId());
                                    }
                                },
                                () -> problems.add("Evidence not found: " + item.getEvidenceId())
                        );
            }
        }

        boolean valid = problems.isEmpty();

        // Audit verification
        String action = valid ? "evidence.bundle_verified" : "evidence.bundle_verify_failed";
        String verificationResult = valid ? "valid" : problems.toString();
        auditWriter.write(AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType("evidence_bundle")
                .entityId(bundleId.toString())
                .payloadHash(computeHash(verificationResult))
                .build());

        return new VerifyResponse(bundleId, valid, problems);
    }

    private void validateEvidenceIds(UUID tenantId, List<String> evidenceIds) {
        for (String evidenceIdStr : evidenceIds) {
            evidenceRecordRepository.findByEvidenceIdAndTenantId(evidenceIdStr, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceIdStr));
        }
    }

    private Map<String, Object> buildManifest(CreateBundleRequest request, UUID tenantId) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0");
        manifest.put("bundleType", request.bundleType());
        manifest.put("referenceType", request.referenceType());
        manifest.put("referenceId", request.referenceId());
        manifest.put("tenantId", tenantId.toString());
        manifest.put("evidenceIds", request.evidenceIds());
        if (request.artifactIds() != null) {
            manifest.put("artifactIds", request.artifactIds());
        }
        if (request.metadata() != null) {
            manifest.put("metadata", request.metadata());
        }
        manifest.put("createdAt", Instant.now().toString());
        return manifest;
    }

    private String canonicalJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize manifest", e);
        }
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    private String computeHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
