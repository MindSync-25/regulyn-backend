package com.regulyn.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.consent.client.EvidenceClient;
import com.regulyn.consent.entity.CommunicationChannel;
import com.regulyn.consent.entity.CommunicationConsentLedger;
import com.regulyn.consent.entity.CommunicationConsentState;
import com.regulyn.consent.entity.NoticeLanguageText;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.CommunicationConsentLedgerRepository;
import com.regulyn.consent.repository.LatestLedgerRow;
import com.regulyn.consent.repository.NoticeLanguageTextRepository;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CommunicationConsentService {

    private static final String HASH_REGEX = "^[a-fA-F0-9]{64}$";

    private final CommunicationConsentLedgerRepository ledgerRepository;
    private final NoticeLanguageTextRepository noticeLanguageTextRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final EvidenceClient evidenceClient;
    private final String serviceName;

    public CommunicationConsentService(
            CommunicationConsentLedgerRepository ledgerRepository,
            NoticeLanguageTextRepository noticeLanguageTextRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            EvidenceClient evidenceClient,
            @Value("${spring.application.name:consent-service}") String serviceName) {
        this.ledgerRepository = ledgerRepository;
        this.noticeLanguageTextRepository = noticeLanguageTextRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.evidenceClient = evidenceClient;
        this.serviceName = serviceName;
    }

    @Transactional
    public CommunicationConsentResponse recordOptIn(CommunicationChannel channel, CommunicationConsentRequest request) {
        return recordConsent(channel, CommunicationConsentState.OPTED_IN, request);
    }

    @Transactional
    public CommunicationConsentResponse recordOptOut(CommunicationChannel channel, CommunicationConsentRequest request) {
        return recordConsent(channel, CommunicationConsentState.OPTED_OUT, request);
    }

    @Transactional(readOnly = true)
    public CommunicationConsentStatusResponse getStatus(CommunicationChannel channel, UUID dataPrincipalId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        try {
            Optional<CommunicationConsentLedger> latest = ledgerRepository
                .findFirstByTenantIdAndDataPrincipalIdAndChannelOrderByEffectiveAtDesc(tenantId, dataPrincipalId, channel);

            if (latest.isEmpty()) {
            return new CommunicationConsentStatusResponse(
                tenantId,
                dataPrincipalId,
                channel.name(),
                CommunicationConsentState.UNKNOWN.name(),
                null,
                null,
                null
            );
            }

            CommunicationConsentLedger ledger = latest.get();
            return new CommunicationConsentStatusResponse(
                tenantId,
                dataPrincipalId,
                channel.name(),
                ledger.getState().name(),
                ledger.getEffectiveAt(),
                ledger.getId(),
                ledger.getConsentTextHashSha256()
            );
        } catch (Exception ex) {
            return new CommunicationConsentStatusResponse(
                tenantId,
                dataPrincipalId,
                channel.name(),
                CommunicationConsentState.UNKNOWN.name(),
                null,
                null,
                null
            );
        }
    }

    @Transactional(readOnly = true)
    public CommunicationConsentBatchResponse batchStatus(CommunicationChannel channel, List<UUID> principalIds) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (principalIds == null || principalIds.isEmpty()) {
            return new CommunicationConsentBatchResponse(channel.name(), true, List.of());
        }
        try {
            List<LatestLedgerRow> rows = ledgerRepository.findLatestByTenantAndChannelAndPrincipalIds(
                    tenantId,
                    channel.name(),
                    principalIds
            );

            Map<UUID, LatestLedgerRow> byPrincipal = new HashMap<>();
            for (LatestLedgerRow row : rows) {
                byPrincipal.put(row.getDataPrincipalId(), row);
            }

            List<CommunicationConsentBatchResult> results = new ArrayList<>();
            for (UUID principalId : principalIds) {
                LatestLedgerRow row = byPrincipal.get(principalId);
                if (row == null) {
                    results.add(new CommunicationConsentBatchResult(
                            principalId,
                            CommunicationConsentState.UNKNOWN.name(),
                            false,
                            "NO_LEDGER",
                            null,
                            null
                    ));
                    continue;
                }

                String state = row.getState();
                boolean allowed = CommunicationConsentState.OPTED_IN.name().equals(state);
                String reason;
                if (allowed) {
                    reason = "OK";
                } else if (CommunicationConsentState.OPTED_OUT.name().equals(state)) {
                    reason = "OPTED_OUT";
                } else {
                    reason = "UNKNOWN_FAIL_CLOSED";
                }

                results.add(new CommunicationConsentBatchResult(
                        principalId,
                        state,
                        allowed,
                        reason,
                        row.getLedgerId(),
                        row.getEffectiveAt()
                ));
            }

            return new CommunicationConsentBatchResponse(channel.name(), true, results);
        } catch (Exception ex) {
            List<CommunicationConsentBatchResult> results = new ArrayList<>();
            for (UUID principalId : principalIds) {
                results.add(new CommunicationConsentBatchResult(
                        principalId,
                        CommunicationConsentState.UNKNOWN.name(),
                        false,
                        "ERROR_FAIL_CLOSED",
                        null,
                        null
                ));
            }
            return new CommunicationConsentBatchResponse(channel.name(), true, results);
        }
    }

    private CommunicationConsentResponse recordConsent(CommunicationChannel channel,
                                                       CommunicationConsentState state,
                                                       CommunicationConsentRequest request) {
        if (request == null || request.dataPrincipalId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "dataPrincipalId is required");
        }
        if (request.source() == null || request.source().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "source is required");
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        UUID actorId = TenantContextHolder.getUserId();
        Instant effectiveAt = request.effectiveAt() != null ? request.effectiveAt() : Instant.now();
        Instant bucket = effectiveAt.truncatedTo(ChronoUnit.MINUTES);

        Optional<CommunicationConsentLedger> existing = ledgerRepository
                .findByTenantIdAndDataPrincipalIdAndChannelAndEffectiveTimeBucketAndState(
                        tenantId,
                        request.dataPrincipalId(),
                        channel,
                        bucket,
                        state);

        if (existing.isPresent()) {
            CommunicationConsentLedger ledger = existing.get();
            return new CommunicationConsentResponse(
                    ledger.getId(),
                    true,
                    tenantId,
                    ledger.getDataPrincipalId(),
                    channel.name(),
                    ledger.getState().name(),
                    ledger.getEffectiveAt(),
                    ledger.getEffectiveTimeBucket(),
                    ledger.getConsentTextHashSha256(),
                    ledger.getNoticeLanguageTextId(),
                    ledger.getEvidenceArtifactId()
            );
        }

        NoticeLanguageText noticeLanguageText = null;
        String consentHash = null;
        if (request.consentTextHash() != null && !request.consentTextHash().isBlank()) {
            if (!request.consentTextHash().matches(HASH_REGEX)) {
                throw new ResponseStatusException(BAD_REQUEST, "consentTextHash must be 64-hex sha256");
            }
            consentHash = request.consentTextHash().toLowerCase(Locale.ROOT);
        } else if (request.noticeLanguageTextId() != null) {
            noticeLanguageText = noticeLanguageTextRepository.findById(request.noticeLanguageTextId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "noticeLanguageTextId not found"));
            if (!tenantId.equals(noticeLanguageText.getTenantId())) {
                throw new ResponseStatusException(BAD_REQUEST, "noticeLanguageTextId not found");
            }
            consentHash = noticeLanguageText.getContentHash();
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "consentTextHash or noticeLanguageTextId required");
        }

        String languageCode = request.language();
        if ((languageCode == null || languageCode.isBlank()) && noticeLanguageText != null) {
            languageCode = noticeLanguageText.getLanguage();
        }

        CommunicationConsentLedger ledger = new CommunicationConsentLedger();
        ledger.setTenantId(tenantId);
        ledger.setDataPrincipalId(request.dataPrincipalId());
        ledger.setChannel(channel);
        ledger.setState(state);
        ledger.setSource(request.source());
        ledger.setActorId(actorId);
        ledger.setActorType("USER");
        ledger.setEffectiveAt(effectiveAt);
        ledger.setEffectiveTimeBucket(bucket);
        ledger.setLanguageCode(languageCode);
        ledger.setConsentTextHashSha256(consentHash);
        ledger.setNoticeLanguageTextId(request.noticeLanguageTextId());

        try {
            ledger = ledgerRepository.save(ledger);
        } catch (DataIntegrityViolationException ex) {
            Optional<CommunicationConsentLedger> deduped = ledgerRepository
                    .findByTenantIdAndDataPrincipalIdAndChannelAndEffectiveTimeBucketAndState(
                            tenantId,
                            request.dataPrincipalId(),
                            channel,
                            bucket,
                            state);
            if (deduped.isPresent()) {
                CommunicationConsentLedger existingLedger = deduped.get();
                return new CommunicationConsentResponse(
                        existingLedger.getId(),
                        true,
                        tenantId,
                        existingLedger.getDataPrincipalId(),
                        channel.name(),
                        existingLedger.getState().name(),
                        existingLedger.getEffectiveAt(),
                        existingLedger.getEffectiveTimeBucket(),
                        existingLedger.getConsentTextHashSha256(),
                        existingLedger.getNoticeLanguageTextId(),
                        existingLedger.getEvidenceArtifactId()
                );
            }
            throw ex;
        }

        Map<String, Object> auditAliases = buildAliasHashes(request);

        Map<String, Object> optPayloadValues = new LinkedHashMap<>();
        optPayloadValues.put("tenantId", tenantId.toString());
        optPayloadValues.put("dataPrincipalId", request.dataPrincipalId().toString());
        optPayloadValues.put("channel", channel.name());
        optPayloadValues.put("state", state.name());
        optPayloadValues.put("ledgerId", ledger.getId().toString());
        optPayloadValues.put("effectiveAt", effectiveAt.toString());
        optPayloadValues.put("effectiveTimeBucket", bucket.toString());
        optPayloadValues.put("consentTextHashSha256", consentHash);
        optPayloadValues.put("noticeLanguageTextId", request.noticeLanguageTextId() != null ? request.noticeLanguageTextId().toString() : null);
        optPayloadValues.put("source", request.source());

        ObjectNode optOutboxPayload = buildPayload(optPayloadValues);
        ObjectNode optAuditPayload = buildPayload(optPayloadValues);
        addAliases(optAuditPayload, auditAliases);

        writeAuditAndOutbox(
            state == CommunicationConsentState.OPTED_IN ? "COMMUNICATION_OPT_IN" : "COMMUNICATION_OPT_OUT",
            "communication_consent",
            ledger.getId().toString(),
            actorId,
            optAuditPayload,
            optOutboxPayload
        );

        Map<String, Object> snapshotValues = new LinkedHashMap<>();
        snapshotValues.put("tenantId", tenantId.toString());
        snapshotValues.put("dataPrincipalId", request.dataPrincipalId().toString());
        snapshotValues.put("channel", channel.name());
        snapshotValues.put("ledgerId", ledger.getId().toString());
        snapshotValues.put("languageCode", languageCode);
        snapshotValues.put("consentTextHashSha256", consentHash);
        snapshotValues.put("noticeLanguageTextId", request.noticeLanguageTextId() != null ? request.noticeLanguageTextId().toString() : null);

        ObjectNode snapshotOutboxPayload = buildPayload(snapshotValues);
        ObjectNode snapshotAuditPayload = buildPayload(snapshotValues);
        addAliases(snapshotAuditPayload, auditAliases);

        writeAuditAndOutbox(
            "CONSENT_LANGUAGE_SNAPSHOT_STORED",
            "communication_consent",
            ledger.getId().toString(),
            actorId,
            snapshotAuditPayload,
            snapshotOutboxPayload
        );

        Map<String, Object> evidenceValues = new LinkedHashMap<>();
        evidenceValues.put("tenantId", tenantId.toString());
        evidenceValues.put("dataPrincipalId", request.dataPrincipalId().toString());
        evidenceValues.put("channel", channel.name());
        evidenceValues.put("state", state.name());
        evidenceValues.put("ledgerId", ledger.getId().toString());
        evidenceValues.put("effectiveAt", effectiveAt.toString());
        evidenceValues.put("effectiveTimeBucket", bucket.toString());
        evidenceValues.put("consentTextHashSha256", consentHash);
        evidenceValues.put("noticeLanguageTextId", request.noticeLanguageTextId() != null ? request.noticeLanguageTextId().toString() : null);
        evidenceValues.put("source", request.source());

        UUID evidenceId = evidenceClient.createArtifact(
            tenantId,
            state == CommunicationConsentState.OPTED_IN ? "COMMUNICATION_OPT_IN" : "COMMUNICATION_OPT_OUT",
            evidenceValues
        );

        if (evidenceId != null) {
            ledger.setEvidenceArtifactId(evidenceId);
            ledger = ledgerRepository.save(ledger);

                Map<String, Object> evidencePayloadValues = new LinkedHashMap<>();
                evidencePayloadValues.put("tenantId", tenantId.toString());
                evidencePayloadValues.put("dataPrincipalId", request.dataPrincipalId().toString());
                evidencePayloadValues.put("channel", channel.name());
                evidencePayloadValues.put("state", state.name());
                evidencePayloadValues.put("ledgerId", ledger.getId().toString());
                evidencePayloadValues.put("evidenceArtifactId", evidenceId.toString());

                    ObjectNode evidenceOutboxPayload = buildPayload(evidencePayloadValues);
                    ObjectNode evidenceAuditPayload = buildPayload(evidencePayloadValues);
                    addAliases(evidenceAuditPayload, auditAliases);

                    writeAuditAndOutbox(
                        "CONSENT_EVIDENCE_ARTIFACT_STORED",
                        "communication_consent",
                        ledger.getId().toString(),
                        actorId,
                        evidenceAuditPayload,
                        evidenceOutboxPayload
                    );
        }

        return new CommunicationConsentResponse(
                ledger.getId(),
                false,
                tenantId,
                ledger.getDataPrincipalId(),
                channel.name(),
                ledger.getState().name(),
                ledger.getEffectiveAt(),
                ledger.getEffectiveTimeBucket(),
                ledger.getConsentTextHashSha256(),
                ledger.getNoticeLanguageTextId(),
                ledger.getEvidenceArtifactId()
        );
    }

    private Map<String, Object> buildAliasHashes(CommunicationConsentRequest request) {
        Map<String, Object> aliases = new LinkedHashMap<>();
        if (request.userId() != null && !request.userId().isBlank()) {
            aliases.put("userIdHash", sha256(request.userId()));
        }
        if (request.email() != null && !request.email().isBlank()) {
            aliases.put("emailHash", sha256(request.email().toLowerCase(Locale.ROOT)));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            aliases.put("phoneHash", sha256(request.phone()));
        }
        return aliases;
    }

    private void addAliases(ObjectNode payload, Map<String, Object> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : aliases.entrySet()) {
            payload.put(entry.getKey(), entry.getValue().toString());
        }
    }

        private void writeAuditAndOutbox(String eventType,
                         String entityType,
                         String entityId,
                         UUID actorId,
                         ObjectNode auditPayload,
                         ObjectNode outboxPayload) {
        String auditHash = sha256(auditPayload.toString());

        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(TenantContextHolder.getTenantId())
            .actorId(actorId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .payloadHash(auditHash)
            .metadata(auditPayload)
            .build();
        auditWriter.write(auditEvent);

        String outboxHash = sha256(outboxPayload.toString());
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(TenantContextHolder.getTenantId());
        envelope.setActorId(actorId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType(entityType);
        envelope.setEntityId(entityId);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(outboxPayload);
        envelope.setPayloadHash(outboxHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());

        outboxWriter.write(envelope);
    }

    private ObjectNode buildPayload(Map<String, Object> values) {
        ObjectNode node = objectMapper.createObjectNode();
        values.forEach((key, value) -> {
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String str) {
                node.put(key, str);
            } else if (value instanceof Integer integer) {
                node.put(key, integer);
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else if (value instanceof List<?> list) {
                node.putPOJO(key, list);
            } else {
                node.putPOJO(key, value);
            }
        });
        return node;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
