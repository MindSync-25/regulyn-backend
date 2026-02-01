package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_status_history", schema = "children")
public class ConsentStatusHistory {
    
    @Id
    @Column(name = "history_id")
    private UUID historyId;
    
    @Column(name = "consent_id", nullable = false)
    private UUID consentId;
    
    @Column(name = "from_status")
    private String fromStatus;
    
    @Column(name = "to_status", nullable = false)
    private String toStatus;
    
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
    
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;
    
    @Column(name = "reason")
    private String reason;
    
    // Constructors
    public ConsentStatusHistory() {
        this.historyId = UUID.randomUUID();
        this.changedAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getHistoryId() {
        return historyId;
    }
    
    public void setHistoryId(UUID historyId) {
        this.historyId = historyId;
    }
    
    public UUID getConsentId() {
        return consentId;
    }
    
    public void setConsentId(UUID consentId) {
        this.consentId = consentId;
    }
    
    public String getFromStatus() {
        return fromStatus;
    }
    
    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }
    
    public String getToStatus() {
        return toStatus;
    }
    
    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }
    
    public Instant getChangedAt() {
        return changedAt;
    }
    
    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
    
    public UUID getChangedBy() {
        return changedBy;
    }
    
    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
