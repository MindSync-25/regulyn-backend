package com.regulyn.retention.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public class CreateDeletionRequest {
    
    @NotNull(message = "Subject ID is required")
    private UUID subjectId;
    
    @NotBlank(message = "Subject type is required")
    private String subjectType; // CUSTOMER|EMPLOYEE|VENDOR|OTHER
    
    @NotBlank(message = "Entity type is required")
    private String entityType;
    
    private String reason;
    
    @NotBlank(message = "Source is required")
    private String source; // DSAR|RETENTION|ADMIN
    
    private Boolean requiresApproval = true;
    private Boolean proofRequired = true;
    
    @Min(value = 1, message = "Due in days must be positive")
    private Integer dueInDays = 30;
    
    private Map<String, Object> metadata;
    
    // Getters and setters
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    
    public Boolean getProofRequired() { return proofRequired; }
    public void setProofRequired(Boolean proofRequired) { this.proofRequired = proofRequired; }
    
    public Integer getDueInDays() { return dueInDays; }
    public void setDueInDays(Integer dueInDays) { this.dueInDays = dueInDays; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
