package com.regulyn.retention.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeletionDetailResponse {
    
    private UUID deletionId;
    private UUID subjectId;
    private String subjectType;
    private String entityType;
    private String status;
    private String source;
    private String reason;
    private Boolean requiresApproval;
    private Boolean proofRequired;
    private UUID assignedTo;
    private UUID approvedBy;
    private Instant approvedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant dueAt;
    private Instant closedAt;
    private UUID evidenceBundleId;
    private Map<String, Object> metadata;
    
    // Getters and setters
    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }
    
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    
    public Boolean getProofRequired() { return proofRequired; }
    public void setProofRequired(Boolean proofRequired) { this.proofRequired = proofRequired; }
    
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
    
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    
    public UUID getEvidenceBundleId() { return evidenceBundleId; }
    public void setEvidenceBundleId(UUID evidenceBundleId) { this.evidenceBundleId = evidenceBundleId; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
