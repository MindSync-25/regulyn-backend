package com.regulyn.retention.model;

import java.util.UUID;

public class CreateRetentionRuleResponse {
    
    private UUID ruleId;
    private Boolean enabled;
    
    public CreateRetentionRuleResponse() {}
    
    public CreateRetentionRuleResponse(UUID ruleId, Boolean enabled) {
        this.ruleId = ruleId;
        this.enabled = enabled;
    }
    
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
