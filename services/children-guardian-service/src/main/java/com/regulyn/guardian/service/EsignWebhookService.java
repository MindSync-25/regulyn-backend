package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.client.EvidenceServiceClient;
import com.regulyn.guardian.entity.ConsentSignedArtifact;
import com.regulyn.guardian.entity.EsignRequestEntity;
import com.regulyn.guardian.entity.EsignWebhookEventEntity;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.esign.*;
import com.regulyn.guardian.repository.ConsentSignedArtifactRepository;
import com.regulyn.guardian.repository.EsignRequestRepository;
import com.regulyn.guardian.repository.EsignWebhookEventRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EsignWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(EsignWebhookService.class);

    private final EsignProviderRegistry providerRegistry;
    private final EsignWebhookEventRepository webhookRepository;
    private final EsignRequestRepository esignRequestRepository;
    private final ConsentSignedArtifactRepository artifactRepository;
    private final GuardianConsentRepository consentRepository;
    private final EvidenceServiceClient evidenceServiceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public EsignWebhookService(
            EsignProviderRegistry providerRegistry,
            EsignWebhookEventRepository webhookRepository,
            EsignRequestRepository esignRequestRepository,
            ConsentSignedArtifactRepository artifactRepository,
            GuardianConsentRepository consentRepository,
            EvidenceServiceClient evidenceServiceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.providerRegistry = providerRegistry;
        this.webhookRepository = webhookRepository;
        this.esignRequestRepository = esignRequestRepository;
        this.artifactRepository = artifactRepository;
        this.consentRepository = consentRepository;
        this.evidenceServiceClient = evidenceServiceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public WebhookResult handleWebhook(UUID tenantId, ProviderId providerId, Map<String, String> headers, byte[] rawBody) {
        EsignProvider provider = providerRegistry.getProvider(providerId);
        EsignWebhookRequest request = new EsignWebhookRequest(tenantId, providerId, headers, rawBody);
        EsignWebhookParseResult parseResult = provider.parseAndVerifyWebhook(request);

        if (parseResult.providerEventId() == null || parseResult.providerEnvelopeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing provider identifiers in webhook payload");
        }

        boolean alreadyExists = webhookRepository.existsByTenantIdAndProviderAndProviderEnvelopeIdAndProviderEventId(
                tenantId,
                providerId.name(),
                parseResult.providerEnvelopeId(),
                parseResult.providerEventId());

        if (alreadyExists) {
            return WebhookResult.alreadyProcessedResult();
        }

        EsignWebhookEventEntity eventEntity = new EsignWebhookEventEntity();
        eventEntity.setTenantId(tenantId);
        eventEntity.setProvider(providerId.name());
        eventEntity.setProviderEnvelopeId(parseResult.providerEnvelopeId());
        eventEntity.setProviderEventId(parseResult.providerEventId());
        eventEntity.setReceivedAt(Instant.now());
        eventEntity.setRawPayload(parseResult.rawPayloadJson());
        eventEntity.setPayloadSha256(parseResult.payloadSha256Hex());
        eventEntity.setSignatureHeader(parseResult.signatureHeader());
        eventEntity.setSignatureVerificationStatus(parseResult.verificationStatus().name());
        eventEntity.setSignatureVerificationError(parseResult.verificationError());

        webhookRepository.save(eventEntity);

        Optional<EsignRequestEntity> esignRequest = esignRequestRepository
                .findByTenantIdAndProviderAndProviderEnvelopeId(tenantId, providerId.name(), parseResult.providerEnvelopeId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("provider", providerId.name());
        payload.put("providerEnvelopeId", parseResult.providerEnvelopeId());
        payload.put("providerEventId", parseResult.providerEventId());
        payload.put("verificationStatus", parseResult.verificationStatus().name());
        esignRequest.ifPresent(req -> {
            payload.put("esignRequestId", req.getId().toString());
            payload.put("childId", req.getChildId().toString());
            payload.put("guardianId", req.getGuardianId().toString());
            if (req.getConsentId() != null) {
                payload.put("consentId", req.getConsentId().toString());
            }
        });

        writeAudit(tenantId, null, "ESIGN_WEBHOOK_RECEIVED", "EsignWebhookEvent", eventEntity.getId().toString(), payload);
        writeOutbox(tenantId, "esign.webhook_received", "EsignWebhookEvent", eventEntity.getId().toString(), payload, parseResult.providerEventId());

        if (parseResult.verificationStatus() == EsignVerificationStatus.VERIFIED) {
            writeAudit(tenantId, null, "ESIGN_SIGNATURE_VERIFIED", "EsignWebhookEvent", eventEntity.getId().toString(), payload);
            writeOutbox(tenantId, "esign.signature_verified", "EsignWebhookEvent", eventEntity.getId().toString(), payload, parseResult.providerEventId());
        } else if (parseResult.verificationStatus() == EsignVerificationStatus.FAILED) {
            writeAudit(tenantId, null, "ESIGN_SIGNATURE_FAILED", "EsignWebhookEvent", eventEntity.getId().toString(), payload);
            writeOutbox(tenantId, "esign.signature_failed", "EsignWebhookEvent", eventEntity.getId().toString(), payload, parseResult.providerEventId());
            return WebhookResult.signatureFailedResult();
        } else {
            return WebhookResult.acceptedResult();
        }

        Optional<SignedDoc> signedDoc = parseResult.signedDoc();
        if (signedDoc.isEmpty()) {
            return WebhookResult.acceptedResult();
        }

        if (esignRequest.isEmpty()) {
            logger.warn("No esign request found for envelope {}", parseResult.providerEnvelopeId());
            return WebhookResult.acceptedResult();
        }

        EsignRequestEntity requestEntity = esignRequest.get();
        UUID consentId = requestEntity.getConsentId();
        if (consentId == null) {
            logger.warn("eSign request {} has no consentId; skipping artifact storage", requestEntity.getId());
            return WebhookResult.acceptedResult();
        }

        String signedPayloadSha256 = sha256Hex(signedDoc.get().bytes());

        EvidenceServiceClient.EvidenceArtifactResponse artifactResponse = evidenceServiceClient.storeSignedArtifact(
                tenantId,
                requestEntity.getChildId(),
                requestEntity.getGuardianId(),
                consentId,
                requestEntity.getProviderEnvelopeId(),
                requestEntity.getDocType(),
                signedDoc.get().mime(),
                signedDoc.get().filename(),
                signedPayloadSha256,
                signedDoc.get().bytes()
        );

        ConsentSignedArtifact artifact = null;
        if (consentId != null) {
            artifact = artifactRepository.findByConsentId(consentId).orElseGet(ConsentSignedArtifact::new);
        }
        if (artifact == null) {
            artifact = new ConsentSignedArtifact();
        }

        if (consentId != null) {
            artifact.setConsentId(consentId);
        }
        artifact.setTenantId(tenantId);
        artifact.setArtifactRef(artifactResponse.artifactRef());
        artifact.setArtifactType(requestEntity.getDocType());
        artifact.setContentHash(signedPayloadSha256);
        artifact.setSignedAt(Instant.now());
        artifact.setEsignRequestId(requestEntity.getId());
        artifact.setProvider(providerId.name());
        artifact.setProviderEnvelopeId(requestEntity.getProviderEnvelopeId());
        artifact.setSignedPayloadSha256(signedPayloadSha256);
        artifact.setArtifactMime(signedDoc.get().mime());
        artifact.setArtifactStoredAt(Instant.now());
        artifact.setSignatureVerified(true);
        artifact.setSignatureVerifiedAt(Instant.now());
        artifact.setSignatureVerificationError(null);

        artifactRepository.save(artifact);

        requestEntity.setStatus("SIGNED");
        requestEntity.setUpdatedAt(Instant.now());
        esignRequestRepository.save(requestEntity);

        if (consentId != null) {
            Optional<GuardianConsent> consent = consentRepository.findByTenantIdAndConsentId(tenantId, consentId);
            if (consent.isPresent()) {
                GuardianConsent c = consent.get();
                c.setSignedArtifactId(artifact.getArtifactId());
                if (c.getEsignRequestId() == null) {
                    c.setEsignRequestId(requestEntity.getId());
                }
                consentRepository.save(c);
            }
        }

        Map<String, Object> artifactPayload = new HashMap<>(payload);
        artifactPayload.put("esignRequestId", requestEntity.getId().toString());
        artifactPayload.put("artifactRef", artifactResponse.artifactRef());
        artifactPayload.put("sha256", signedPayloadSha256);

        writeAudit(tenantId, null, "GUARDIAN_SIGNED_ARTIFACT_STORED", "ConsentSignedArtifact", artifact.getArtifactId().toString(), artifactPayload);
        writeOutbox(tenantId, "guardian.signed_artifact_stored", "ConsentSignedArtifact", artifact.getArtifactId().toString(), artifactPayload, parseResult.providerEventId());

        return WebhookResult.completedResult();
    }

    private void writeAudit(UUID tenantId, UUID actorId, String action, String entityType, String entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorType(actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                    .service("children-guardian-service")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadHash(hash)
                    .build();
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }

    private void writeOutbox(UUID tenantId, String eventType, String entityType, String entityId, Map<String, ?> payload, String idempotencyKey) {
        try {
            TenantContext context = new TenantContext(tenantId, null, null, null, idempotencyKey);
            TenantContextHolder.setContext(context);
            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    "children-guardian-service",
                    entityType,
                    entityId,
                    payload,
                    idempotencyKey
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox event", e);
        } finally {
            TenantContextHolder.clear();
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
        } catch (Exception e) {
            return "";
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }

    public record WebhookResult(boolean processed, boolean signatureFailed) {
        public static WebhookResult alreadyProcessedResult() { return new WebhookResult(true, false); }
        public static WebhookResult signatureFailedResult() { return new WebhookResult(false, true); }
        public static WebhookResult acceptedResult() { return new WebhookResult(false, false); }
        public static WebhookResult completedResult() { return new WebhookResult(true, false); }
    }
}
