package io.regulyn.connector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateTargetRequest {

    @NotBlank(message = "targetKey is required")
    private String targetKey;

    @NotNull(message = "targetType is required")
    private TargetType targetType;

    @NotNull(message = "subjectType is required")
    private SubjectType subjectType;

    @NotEmpty(message = "supportedActions cannot be empty")
    private List<JobAction> supportedActions;

    private Boolean requiresApproval = true;

    private Map<String, Object> metadata = new HashMap<>();

    // Getters and Setters
    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public List<JobAction> getSupportedActions() {
        return supportedActions;
    }

    public void setSupportedActions(List<JobAction> supportedActions) {
        this.supportedActions = supportedActions;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public enum TargetType {
        RECORD, FILE, TABLE, OBJECT
    }

    public enum SubjectType {
        CUSTOMER, EMPLOYEE, VENDOR, OTHER
    }

    public enum JobAction {
        DELETE, EXPORT, AUDIT_PULL
    }
}
