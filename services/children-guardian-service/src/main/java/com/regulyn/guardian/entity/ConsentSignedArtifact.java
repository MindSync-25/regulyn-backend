package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_signed_artifacts", schema = "children")
public class ConsentSignedArtifact {
    
    @Id
    @Column(name = "artifact_id")
    private UUID artifactId;
    
    @Column(name = "consent_id", nullable = false)
    private UUID consentId;
    
    @Column(name = "artifact_ref", nullable = false)
    private String artifactRef;
    
    @Column(name = "artifact_type", nullable = false)
    private String artifactType;
    
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    
    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;
    
    // Constructors
    public ConsentSignedArtifact() {
        this.artifactId = UUID.randomUUID();
    }
    
    // Getters and Setters
    public UUID getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }
    
    public UUID getConsentId() {
        return consentId;
    }
    
    public void setConsentId(UUID consentId) {
        this.consentId = consentId;
    }
    
    public String getArtifactRef() {
        return artifactRef;
    }
    
    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }
    
    public String getArtifactType() {
        return artifactType;
    }
    
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }
    
    public String getContentHash() {
        return contentHash;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    
    public Instant getSignedAt() {
        return signedAt;
    }
    
    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }
}
