package com.regulyn.retention.model;

import java.util.UUID;

public class CreateRetentionCandidateResponse {
    
    private UUID candidateId;
    
    public CreateRetentionCandidateResponse() {}
    
    public CreateRetentionCandidateResponse(UUID candidateId) {
        this.candidateId = candidateId;
    }
    
    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }
}
