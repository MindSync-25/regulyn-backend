package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.client.EvidenceReportingClient;
import com.regulyn.guardian.entity.ChildrenEvidenceExportEntity;
import com.regulyn.guardian.entity.ConsentSignedArtifact;
import com.regulyn.guardian.entity.EsignRequestEntity;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvidenceExportService {

    private static final Logger logger = LoggerFactory.getLogger(EvidenceExportService.class);

    private final ChildrenEvidenceExportRepository exportRepository;
    private final GuardianConsentRepository consentRepository;
    private final ConsentStatusHistoryRepository historyRepository;
    private final ConsentSignedArtifactRepository artifactRepository;
    private final EsignRequestRepository esignRequestRepository;
    private final EsignWebhookEventRepository webhookEventRepository;
    private final ChildMajorityTransitionRepository transitionRepository;
    private final EvidenceReportingClient evidenceReportingClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public EvidenceExportService(
            ChildrenEvidenceExportRepository exportRepository,
            GuardianConsentRepository consentRepository,
            ConsentStatusHistoryRepository historyRepository,
            ConsentSignedArtifactRepository artifactRepository,
            EsignRequestRepository esignRequestRepository,
            EsignWebhookEventRepository webhookEventRepository,
            ChildMajorityTransitionRepository transitionRepository,
            EvidenceReportingClient evidenceReportingClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.exportRepository = exportRepository;
        this.consentRepository = consentRepository;
        this.historyRepository = historyRepository;
        this.artifactRepository = artifactRepository;
        this.esignRequestRepository = esignRequestRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.transitionRepository = transitionRepository;
        this.evidenceReportingClient = evidenceReportingClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public EvidenceExportResponse createChildEvidenceExport(UUID childId, String exportScope, String idempotencyKey) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }

        Optional<ChildrenEvidenceExportEntity> existing = exportRepository
                .findByTenantIdAndChildIdAndExportScopeAndIdempotencyKey(tenantId, childId, exportScope, idempotencyKey);
        if (existing.isPresent()) {
            ChildrenEvidenceExportEntity entity = existing.get();
            return new EvidenceExportResponse(
                    entity.getId(),
                    entity.getStatus(),
                    entity.getEvidenceBundleRef(),
                    entity.getEvidenceBundleSha256()
            );
        }

        ChildrenEvidenceExportEntity exportEntity = new ChildrenEvidenceExportEntity();
        exportEntity.setTenantId(tenantId);
        exportEntity.setChildId(childId);
        exportEntity.setExportScope(exportScope);
        exportEntity.setStatus("PENDING");
        exportEntity.setRequestedBy(actorId);
        exportEntity.setIdempotencyKey(idempotencyKey);
        exportRepository.save(exportEntity);

        Map<String, Object> payload = buildEvidencePayload(tenantId, childId, exportScope);

        try {
            EvidenceReportingClient.EvidenceBundleResponse response = evidenceReportingClient.createBundle(payload);
            exportEntity.setEvidenceBundleRef(response.bundleRef());
            exportEntity.setEvidenceBundleSha256(response.bundleSha256());
            exportEntity.setStatus("STORED");
            exportRepository.save(exportEntity);

            Map<String, Object> eventPayload = Map.of(
                    "tenantId", tenantId.toString(),
                    "childId", childId.toString(),
                    "exportId", exportEntity.getId().toString(),
                    "exportScope", exportScope,
                    "bundleRef", response.bundleRef(),
                    "bundleSha256", response.bundleSha256()
            );

            writeAudit(actorId, "CHILD_EVIDENCE_BUNDLE_CREATED", "Child", childId, eventPayload);
            writeOutbox(tenantId, "child.evidence_bundle_created", "Child", childId.toString(), eventPayload);

            return new EvidenceExportResponse(exportEntity.getId(), exportEntity.getStatus(), response.bundleRef(), response.bundleSha256());
        } catch (ResponseStatusException e) {
            exportEntity.setStatus("FAILED");
            exportEntity.setLastError(e.getReason());
            exportEntity.setLastErrorAt(Instant.now());
            exportRepository.save(exportEntity);

            writeAudit(actorId, "CHILD_EVIDENCE_BUNDLE_FAILED", "Child", childId,
                    Map.of("error", e.getReason() != null ? e.getReason() : "evidence_export_failed"));

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence service unavailable");
        }
    }

    private Map<String, Object> buildEvidencePayload(UUID tenantId, UUID childId, String exportScope) {
        List<GuardianConsent> consents = consentRepository.findByTenantIdAndChildId(tenantId, childId);
        List<UUID> consentIds = consents.stream().map(GuardianConsent::getConsentId).toList();

        List<Map<String, Object>> consentItems = consents.stream()
                .map(consent -> Map.<String, Object>of(
                        "consentId", consent.getConsentId().toString(),
                        "status", consent.getStatus(),
                        "purposeKey", consent.getPurposeKey(),
                        "createdAt", consent.getCreatedAt().toString()
                ))
                .toList();

        List<Map<String, Object>> historyItems = consentIds.stream()
                .flatMap(id -> historyRepository.findByConsentIdOrderByChangedAtAsc(id).stream())
                .map(history -> Map.<String, Object>of(
                        "consentId", history.getConsentId().toString(),
                        "fromStatus", history.getFromStatus(),
                        "toStatus", history.getToStatus(),
                        "changedAt", history.getChangedAt().toString()
                ))
                .toList();

        List<ConsentSignedArtifact> artifacts = consentIds.stream()
                .flatMap(id -> artifactRepository.findAllByConsentId(id).stream())
                .toList();

        List<Map<String, Object>> artifactItems = artifacts.stream()
                .map(artifact -> Map.<String, Object>of(
                        "consentId", artifact.getConsentId().toString(),
                        "artifactRef", artifact.getArtifactRef(),
                        "sha256", artifact.getSignedPayloadSha256() != null ? artifact.getSignedPayloadSha256() : ""
                ))
                .toList();

        List<EsignRequestEntity> esignRequests = esignRequestRepository.findAll()
                .stream()
                .filter(req -> tenantId.equals(req.getTenantId()) && childId.equals(req.getChildId()))
                .toList();

        List<Map<String, Object>> esignItems = esignRequests.stream()
                .map(req -> Map.<String, Object>of(
                        "esignRequestId", req.getId().toString(),
                        "provider", req.getProvider(),
                        "status", req.getStatus(),
                        "providerEnvelopeId", req.getProviderEnvelopeId()
                ))
                .toList();

        List<String> envelopeIds = esignRequests.stream()
                .map(EsignRequestEntity::getProviderEnvelopeId)
                .distinct()
                .toList();

        List<Map<String, Object>> webhookItems = webhookEventRepository.findAll()
                .stream()
                .filter(event -> tenantId.equals(event.getTenantId()) && envelopeIds.contains(event.getProviderEnvelopeId()))
                .map(event -> Map.<String, Object>of(
                        "webhookId", event.getId().toString(),
                        "providerEnvelopeId", event.getProviderEnvelopeId(),
                        "verificationStatus", event.getSignatureVerificationStatus()
                ))
                .toList();

        List<Map<String, Object>> transitionItems = transitionRepository.findAll()
                .stream()
                .filter(transition -> tenantId.equals(transition.getTenantId()) && childId.equals(transition.getChildId()))
                .map(transition -> Map.<String, Object>of(
                        "transitionId", transition.getId().toString(),
                        "majorityDate", transition.getMajorityDate().toString(),
                        "status", transition.getTransitionStatus(),
                        "adultConsentReceiptRef", transition.getAdultConsentReceiptRef() != null ? transition.getAdultConsentReceiptRef() : ""
                ))
                .toList();

        return new HashMap<>() {{
            put("tenantId", tenantId.toString());
            put("childId", childId.toString());
            put("exportScope", exportScope);
            put("consents", consentItems);
            put("consentHistory", historyItems);
            put("signedArtifacts", artifactItems);
            put("esignRequests", esignItems);
            put("esignWebhooks", webhookItems);
            put("majorityTransitions", transitionItems);
        }};
    }

    private void writeAudit(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            TenantContext context = TenantContextHolder.getContext();

            AuditEvent event = AuditEvent.builder()
                    .tenantId(context.getTenantId())
                    .actorId(actorId)
                    .actorType(actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
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

    private void writeOutbox(UUID tenantId, String eventType, String entityType, String entityId, Map<String, ?> payload) {
        try {
            Map<String, Object> safePayload = new HashMap<>();
            payload.forEach((k, v) -> safePayload.put(k, v != null ? v.toString() : ""));

            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    "children-guardian-service",
                    entityType,
                    entityId,
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

    public record EvidenceExportResponse(UUID exportId, String status, String bundleRef, String bundleSha256) {
    }
}
