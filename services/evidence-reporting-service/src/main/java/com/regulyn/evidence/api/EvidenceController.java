package com.regulyn.evidence.api;

import com.regulyn.evidence.model.EvidenceRequest;
import com.regulyn.evidence.model.EvidenceResponse;
import com.regulyn.evidence.service.EvidenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/evidence")
public class EvidenceController {
  
  private final EvidenceService evidenceService;
  
  public EvidenceController(EvidenceService evidenceService) {
    this.evidenceService = evidenceService;
  }
  
  @PostMapping
  public EvidenceResponse submitEvidence(@RequestBody EvidenceRequest request) {
    return evidenceService.submitEvidence(request);
  }
}
