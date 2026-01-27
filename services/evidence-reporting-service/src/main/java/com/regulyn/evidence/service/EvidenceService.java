package com.regulyn.evidence.service;

import com.regulyn.evidence.model.EvidenceRequest;
import com.regulyn.evidence.model.EvidenceResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EvidenceService {
  
  private final Map<String, EvidenceRequest> evidenceStore = new HashMap<>();
  
  public EvidenceResponse submitEvidence(EvidenceRequest request) {
    String evidenceId = UUID.randomUUID().toString();
    evidenceStore.put(evidenceId, request);
    return new EvidenceResponse(evidenceId, "STORED");
  }
}
