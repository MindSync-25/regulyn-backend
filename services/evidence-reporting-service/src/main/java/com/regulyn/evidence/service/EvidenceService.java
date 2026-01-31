package com.regulyn.evidence.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.evidence.entity.EvidenceRecord;
import com.regulyn.evidence.model.EvidenceRequest;
import com.regulyn.evidence.model.EvidenceResponse;
import com.regulyn.evidence.repository.EvidenceRecordRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EvidenceService {

  private final EvidenceRecordRepository evidenceRepository;
  private final OutboxWriter outboxWriter;

  public EvidenceService(EvidenceRecordRepository evidenceRepository, OutboxWriter outboxWriter) {
    this.evidenceRepository = evidenceRepository;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public EvidenceResponse submitEvidence(EvidenceRequest request) {
    TenantContext context = TenantContextHolder.getContext();
    
    String evidenceId = UUID.randomUUID().toString();
    String evidenceData = request.getEvidenceType() + "|" + request.getDescription();
    String evidenceHash = EventHasher.sha256(evidenceData);
    
    // Persist evidence record
    EvidenceRecord record = new EvidenceRecord();
    record.setTenantId(context.getTenantId());
    record.setEvidenceId(evidenceId);
    record.setEvidenceType(request.getEvidenceType());
    record.setEvidenceHash(evidenceHash);
    record.setCreatedBy(context.getUserId());
    
    evidenceRepository.save(record);
    
    // Create event for outbox
    Map<String, Object> eventPayload = new HashMap<>();
    eventPayload.put("evidenceId", evidenceId);
    eventPayload.put("evidenceType", request.getEvidenceType());
    eventPayload.put("payloadHash", evidenceHash);
    
    EventEnvelopeV1 envelope = EventFactory.create(
        "evidence.created",
        "evidence-reporting-service",
        "EVIDENCE",
        evidenceId,
        eventPayload
    );
    
    outboxWriter.write(envelope);
    
    return new EvidenceResponse(evidenceId, "STORED");
  }
}
