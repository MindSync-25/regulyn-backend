package com.regulyn.nominee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.nominee.audit.NomineeAuditWriter;
import com.regulyn.nominee.client.EvidenceClient;
import com.regulyn.nominee.dto.RightsGrantResponse;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.entity.NomineeDocument;
import com.regulyn.nominee.entity.RightsGrant;
import com.regulyn.nominee.repository.NomineeClaimRepository;
import com.regulyn.nominee.repository.NomineeDocumentRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import com.regulyn.nominee.repository.RightsGrantRepository;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RightsService {

    private static final Logger log = LoggerFactory.getLogger(RightsService.class);

    private final RightsGrantRepository grantsRepository;
    private final OutboxEventPublisher eventPublisher;
    private final EvidenceClient evidenceClient;
    private final NomineeRepository nomineeRepository;
    private final NomineeClaimRepository nomineeClaimRepository;
    private final NomineeDocumentRepository nomineeDocumentRepository;
    private final NomineeAuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public RightsService(RightsGrantRepository grantsRepository,
                         OutboxEventPublisher eventPublisher,
                         EvidenceClient evidenceClient,
                         NomineeRepository nomineeRepository,
                         NomineeClaimRepository nomineeClaimRepository,
                         NomineeDocumentRepository nomineeDocumentRepository,
                         NomineeAuditWriter auditWriter,
                         OutboxWriter outboxWriter,
                         ObjectMapper objectMapper,
                         @Value("${spring.application.name:nominee-service}") String serviceName) {
        this.grantsRepository = grantsRepository;
        this.eventPublisher = eventPublisher;
        this.evidenceClient = evidenceClient;
        this.nomineeRepository = nomineeRepository;
        this.nomineeClaimRepository = nomineeClaimRepository;
        this.nomineeDocumentRepository = nomineeDocumentRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Transactional
    public RightsGrantResponse grantRights(UUID tenantId, UUID dataPrincipalId, UUID nomineeId, 
                                           String scope, LocalDate validFrom, LocalDate validTo,
                                           UUID claimId, UUID grantedBy) {
        log.info("Granting rights: tenant={}, principal={}, nominee={}, scope={}", 
            tenantId, dataPrincipalId, nomineeId, scope);

        RightsGrant grant = new RightsGrant();
        grant.setTenantId(tenantId);
        grant.setDataPrincipalId(dataPrincipalId);
        grant.setNomineeId(nomineeId);
        grant.setScope(scope);
        grant.setStatus("ACTIVE");
        grant.setGrantedAt(Instant.now());
        grant.setGrantedBy(grantedBy);
        grant.setValidFrom(validFrom);
        grant.setValidTo(validTo);
        grant.setClaimId(claimId);

        grant = grantsRepository.save(grant);

        eventPublisher.publish("rights.granted", grant.getId().toString(), grant);

        if (claimId != null) {
            Nominee nominee = nomineeRepository.findById(nomineeId)
                    .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));
            NomineeClaim claim = nomineeClaimRepository.findById(claimId)
                    .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

            Map<String, Object> bundlePayload = buildBundlePayload(nominee, claim);
            UUID evidenceId = evidenceClient.createEvidence("NOMINEE_RIGHTS_TRANSFER", bundlePayload);
            List<UUID> bundleEvidenceIds = new ArrayList<>();
            bundleEvidenceIds.add(evidenceId);

            boolean bundleCreated = false;
            if (claim.getEvidenceId() == null) {
                UUID bundleId = evidenceClient.createBundle("NOMINEE_RIGHTS_TRANSFER_BUNDLE", bundleEvidenceIds);
                claim.setEvidenceId(bundleId);
                bundleCreated = true;
            } else {
                evidenceClient.updateBundle(claim.getEvidenceId(), bundleEvidenceIds);
            }
            nomineeClaimRepository.save(claim);

            Map<String, Object> eventPayload = new HashMap<>(bundlePayload);
            eventPayload.put("grantId", grant.getId().toString());
            eventPayload.put("bundleId", claim.getEvidenceId() != null ? claim.getEvidenceId().toString() : null);
            writeAuditAndOutbox(tenantId, grantedBy, "CLAIM_RIGHTS_TRANSFER_COMPLETED",
                    "claim.rights_transfer_completed", claimId, eventPayload, null);

            if (bundleCreated) {
                writeAuditAndOutbox(tenantId, grantedBy, "NOMINEE_EVIDENCE_BUNDLE_CREATED",
                        "nominee.evidence_bundle_created", claimId, eventPayload, null);
            } else {
                writeAuditAndOutbox(tenantId, grantedBy, "NOMINEE_EVIDENCE_BUNDLE_UPDATED",
                        "nominee.evidence_bundle_updated", claimId, eventPayload, null);
            }
        }

        log.info("Rights granted: grantId={}", grant.getId());
        return toResponse(grant);
    }

    @Transactional
    public void revokeRights(UUID grantId, UUID revokedBy) {
        log.info("Revoking rights: grantId={}, by={}", grantId, revokedBy);

        RightsGrant grant = grantsRepository.findById(grantId)
            .orElseThrow(() -> new IllegalArgumentException("Rights grant not found: " + grantId));

        grant.setStatus("REVOKED");
        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(revokedBy);

        grantsRepository.save(grant);

        eventPublisher.publish("rights.revoked", grant.getId().toString(), grant);

        log.info("Rights revoked: grantId={}", grantId);
    }

    public List<RightsGrantResponse> getRightsByPrincipal(UUID tenantId, UUID dataPrincipalId) {
        return grantsRepository.findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<RightsGrantResponse> getRightsByNominee(UUID nomineeId) {
        return grantsRepository.findByNomineeId(nomineeId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private RightsGrantResponse toResponse(RightsGrant grant) {
        RightsGrantResponse response = new RightsGrantResponse();
        response.setGrantId(grant.getId());
        response.setDataPrincipalId(grant.getDataPrincipalId());
        response.setNomineeId(grant.getNomineeId());
        response.setScope(grant.getScope());
        response.setStatus(grant.getStatus());
        response.setGrantedAt(grant.getGrantedAt());
        response.setGrantedBy(grant.getGrantedBy());
        response.setValidFrom(grant.getValidFrom());
        response.setValidTo(grant.getValidTo());
        response.setClaimId(grant.getClaimId());
        response.setRevokedAt(grant.getRevokedAt());
        response.setRevokedBy(grant.getRevokedBy());
        return response;
    }

    private Map<String, Object> buildBundlePayload(Nominee nominee, NomineeClaim claim) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", nominee.getTenantId().toString());
        payload.put("nomineeId", nominee.getId().toString());
        payload.put("claimId", claim.getId().toString());

        List<NomineeDocument> docs = nomineeDocumentRepository.findByTenantIdAndNomineeId(nominee.getTenantId(), nominee.getId());
        List<Map<String, Object>> documentEntries = docs.stream()
                .filter(doc -> doc.getClaimId() == null || doc.getClaimId().equals(claim.getId()))
                .map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nomineeDocumentId", doc.getId().toString());
                    item.put("verificationStep", doc.getVerificationStep());
                    item.put("artifactRef", doc.getArtifactRef());
                    item.put("sha256Hash", doc.getSha256Hash());
                    item.put("filename", doc.getFilename());
                    item.put("contentType", doc.getContentType());
                    item.put("sizeBytes", doc.getSizeBytes());
                    if (doc.getUploadedAt() != null) {
                        item.put("uploadedAt", doc.getUploadedAt().toString());
                    }
                    if (doc.getClaimId() != null) {
                        item.put("claimId", doc.getClaimId().toString());
                    }
                    return item;
                })
                .collect(Collectors.toList());
        payload.put("documents", documentEntries);

        Map<String, Object> decision = new HashMap<>();
        decision.put("status", nominee.getStatus());
        decision.put("verificationMethod", nominee.getVerificationMethod());
        if (nominee.getVerifiedAt() != null) {
            decision.put("verifiedAt", nominee.getVerifiedAt().toString());
        }
        String decisionValue = "PENDING";
        if ("VERIFIED".equalsIgnoreCase(nominee.getStatus())) {
            decisionValue = "APPROVED";
        } else if ("REJECTED".equalsIgnoreCase(nominee.getStatus())) {
            decisionValue = "REJECTED";
        }
        decision.put("decision", decisionValue);
        decision.put("decidedBy", null);
        decision.put("decidedAt", nominee.getVerifiedAt() != null ? nominee.getVerifiedAt().toString() : null);
        decision.put("decisionNotes", null);
        decision.put("rejectionReason", null);
        payload.put("verificationDecision", decision);

        boolean exceptionUsed = docs.stream().anyMatch(doc -> "VERIFICATION_EXCEPTION".equals(doc.getVerificationStep()));
        payload.put("exceptionUsed", exceptionUsed);
        docs.stream().filter(doc -> "VERIFICATION_EXCEPTION".equals(doc.getVerificationStep())).findFirst().ifPresent(doc -> {
            Map<String, Object> exception = new HashMap<>();
            exception.put("artifactRef", doc.getArtifactRef());
            exception.put("sha256Hash", doc.getSha256Hash());
            payload.put("exception", exception);
        });

        return payload;
    }

    private void writeAuditAndOutbox(
            UUID tenantId,
            UUID userId,
            String action,
            String eventType,
            UUID entityId,
            Map<String, Object> payload,
            String idempotencyKey) {
        JsonNode metadata = objectMapper.valueToTree(payload);
        String payloadHash = EventHasher.sha256(EventJson.toCanonicalJson(payload));

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(userId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                .service(serviceName)
                .action(action)
                .entityType("RIGHTS_GRANT")
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();

        auditWriter.writeOrThrow(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(userId != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("RIGHTS_GRANT");
        envelope.setEntityId(entityId.toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(entityId.toString());
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }
}
