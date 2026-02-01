package io.regulyn.scanner.service;

import com.regulyn.common.audit.AuditWriter;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.client.RetentionDeletionClient;
import io.regulyn.scanner.dto.PromoteRetentionCandidatesRequest;
import io.regulyn.scanner.dto.PromoteRetentionCandidatesResponse;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanPromotion;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanPromotionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PromotionService {

    private static final Logger log = LoggerFactory.getLogger(PromotionService.class);

    private final ScanFindingRepository scanFindingRepository;
    private final ScanPromotionRepository scanPromotionRepository;
    private final RetentionDeletionClient retentionClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public PromotionService(ScanFindingRepository scanFindingRepository,
                             ScanPromotionRepository scanPromotionRepository,
                             RetentionDeletionClient retentionClient,
                             AuditWriter auditWriter,
                             OutboxWriter outboxWriter) {
        this.scanFindingRepository = scanFindingRepository;
        this.scanPromotionRepository = scanPromotionRepository;
        this.retentionClient = retentionClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PromoteRetentionCandidatesResponse promoteRetentionCandidates(UUID runId, PromoteRetentionCandidatesRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Check if already promoted
        if (scanPromotionRepository.existsByTenantIdAndRunId(tenantId, runId)) {
            throw new IllegalStateException("Run has already been promoted");
        }

        // Build risk level filter
        List<String> riskLevels = getRiskLevelsFromMin(request.getMinRiskLevel());

        // Get candidates
        List<ScanFinding> candidates = scanFindingRepository.findRetentionCandidates(
            tenantId,
            runId,
            riskLevels
        );

        // Apply limit
        int limit = request.getLimit();
        if (candidates.size() > limit) {
            candidates = candidates.subList(0, limit);
        }

        // Promote each candidate
        int promoted = 0;
        int failed = 0;
        Set<UUID> processedSubjects = new HashSet<>();

        String subjectType = request.getSubjectType().name();
        String entityType = request.getEntityType();

        for (ScanFinding candidate : candidates) {
            UUID subjectId = candidate.getSubjectId();
            if (subjectId == null) {
                failed++;
                continue;
            }

            // Avoid double promotion within same run
            if (processedSubjects.contains(subjectId)) {
                continue;
            }

            try {
                String finalEntityType = (entityType != null) ? entityType : candidate.getEntityType();
                retentionClient.registerCandidate(subjectId, subjectType, finalEntityType);
                promoted++;
                processedSubjects.add(subjectId);
            } catch (Exception e) {
                log.error("Failed to promote candidate {}: {}", subjectId, e.getMessage());
                failed++;
            }
        }

        // Record promotion
        ScanPromotion promotion = new ScanPromotion();
        promotion.setTenantId(tenantId);
        promotion.setRunId(runId);
        promotion.setPromotedCount(promoted);
        promotion.setFailedCount(failed);
        promotion.setDetails(Map.of(
            "subjectType", subjectType,
            "minRiskLevel", request.getMinRiskLevel().name(),
            "requestedLimit", limit,
            "candidatesFound", candidates.size()
        ));

        scanPromotionRepository.save(promotion);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.retention_promoted")
                .entityType("SCAN_PROMOTION")
                .entityId(promotion.getPromotionId().toString())
                .payloadHash("N/A")
                .build());

        // Outbox
        EventEnvelopeV1 event = EventFactory.create(
                "scanner.retention_promoted",
                "scanner-service",
                "SCAN_PROMOTION",
                promotion.getPromotionId().toString(),
                Map.of("promotedCount", promoted, "failedCount", failed)
        );
        outboxWriter.write(event);

        PromoteRetentionCandidatesResponse response = new PromoteRetentionCandidatesResponse();
        response.setRunId(runId);
        response.setPromotedCount(promoted);
        response.setFailedCount(failed);
        return response;
    }

    private List<String> getRiskLevelsFromMin(PromoteRetentionCandidatesRequest.RiskLevel minLevel) {
        List<String> levels = new ArrayList<>();
        switch (minLevel) {
            case LOW:
                levels.add("LOW");
                levels.add("MED");
                levels.add("HIGH");
                break;
            case MED:
                levels.add("MED");
                levels.add("HIGH");
                break;
            case HIGH:
                levels.add("HIGH");
                break;
        }
        return levels;
    }
}
