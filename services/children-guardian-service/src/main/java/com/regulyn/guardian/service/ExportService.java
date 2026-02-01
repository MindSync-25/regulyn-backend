package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.client.EvidenceServiceClient;
import com.regulyn.guardian.dto.CreateExportRequest;
import com.regulyn.guardian.dto.CreateExportResponse;
import com.regulyn.guardian.entity.ChildrenExport;
import com.regulyn.guardian.repository.ChildrenExportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ExportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    
    private final ChildrenExportRepository exportRepository;
    private final EvidenceServiceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public ExportService(
            ChildrenExportRepository exportRepository,
            EvidenceServiceClient evidenceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.exportRepository = exportRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Transactional
    public CreateExportResponse createExport(CreateExportRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        try {
            // Create a bundle for the export
            // In a real implementation, you would query guardian consents based on filters
            // and create evidence entries for each consent, then bundle them
            UUID bundleId = evidenceClient.createBundle(
                "GUARDIAN_CONSENT_EXPORT",
                UUID.randomUUID(), // placeholder reference
                java.util.List.of() // would include actual evidence IDs
            );
            
            // Create export from bundle
            UUID evidenceExportId = evidenceClient.createExport(bundleId);
            
            // Save export record
            ChildrenExport export = new ChildrenExport();
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setEvidenceExportId(evidenceExportId);
            export.setStatus("CREATED");
            
            export = exportRepository.save(export);
            
            // Build download path
            String downloadPath = "/exports/guardian-consents/" + export.getExportId() + "/download";
            
            writeAudit(actorId, "children.export_created", "ChildrenExport", export.getExportId(), 
                Map.of("bundleId", bundleId.toString()));
            writeOutbox(tenantId, "children.export_created", Map.of(
                "exportId", export.getExportId().toString(),
                "bundleId", bundleId.toString(),
                "evidenceExportId", evidenceExportId.toString()
            ));
            
            logger.info("Created export: {} with bundle {}", export.getExportId(), bundleId);
            
            return new CreateExportResponse(bundleId, export.getExportId(), downloadPath);
            
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Evidence service unavailable: " + e.getMessage(), e);
        }
    }
    
    @Transactional(readOnly = true)
    public byte[] downloadExport(UUID exportId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        
        ChildrenExport export = exportRepository.findByTenantIdAndExportId(tenantId, exportId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found"));
        
        try {
            return evidenceClient.downloadExport(export.getEvidenceExportId());
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Evidence service unavailable: " + e.getMessage(), e);
        }
    }
    
    private void writeAudit(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            TenantContext context = TenantContextHolder.getContext();
            
            AuditEvent event = AuditEvent.builder()
                .tenantId(context.getTenantId())
                .actorId(actorId)
                .actorType(AuditEvent.ActorType.USER)
                .service("children-guardian-service")
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(hash)
                .build();
            
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }
    
    private void writeOutbox(UUID tenantId, String eventType, Map<String, ?> payload) {
        try {
            Map<String, Object> safePayload = new HashMap<>();
            payload.forEach((k, v) -> safePayload.put(k, v != null ? v.toString() : ""));
            
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "children-guardian-service",
                "ChildrenExport",
                safePayload.getOrDefault("exportId", "").toString(),
                safePayload
            );
            
            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox event", e);
        }
    }
    
    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
