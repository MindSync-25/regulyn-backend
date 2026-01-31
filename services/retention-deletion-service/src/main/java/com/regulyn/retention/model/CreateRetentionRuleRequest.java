package com.regulyn.retention.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateRetentionRuleRequest {
    
    @NotBlank(message = "Rule name is required")
    private String ruleName;
    
    @NotBlank(message = "Subject type is required")
    private String subjectType; // CUSTOMER|EMPLOYEE|VENDOR|OTHER
    
    @NotBlank(message = "Entity type is required")
    private String entityType;
    
    @NotNull(message = "Retention days is required")
    @Min(value = 1, message = "Retention days must be positive")
    private Integer retentionDays;
    
    @NotBlank(message = "Action is required")
    private String action; // DELETE|ANONYMIZE
    
    private Boolean enabled = true;
    
    private Map<String, Object> metadata;
    
    // Getters and setters
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
