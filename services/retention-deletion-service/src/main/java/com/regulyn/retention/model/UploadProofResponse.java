package com.regulyn.retention.model;

import java.util.UUID;

public class UploadProofResponse {
    
    private UUID proofId;
    private String artifactHash;
    private String artifactRef;
    
    public UploadProofResponse() {}
    
    public UploadProofResponse(UUID proofId, String artifactHash, String artifactRef) {
        this.proofId = proofId;
        this.artifactHash = artifactHash;
        this.artifactRef = artifactRef;
    }
    
    public UUID getProofId() { return proofId; }
    public void setProofId(UUID proofId) { this.proofId = proofId; }
    
    public String getArtifactHash() { return artifactHash; }
    public void setArtifactHash(String artifactHash) { this.artifactHash = artifactHash; }
    
    public String getArtifactRef() { return artifactRef; }
    public void setArtifactRef(String artifactRef) { this.artifactRef = artifactRef; }
}
