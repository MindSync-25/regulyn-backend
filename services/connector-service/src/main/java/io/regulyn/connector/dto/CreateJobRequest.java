package io.regulyn.connector.dto;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateJobRequest {

    @NotNull(message = "jobType is required")
    private JobType jobType;

    @NotNull(message = "connectorId is required")
    private UUID connectorId;

    @NotNull(message = "targetId is required")
    private UUID targetId;

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @NotNull(message = "subjectType is required")
    private SubjectType subjectType;

    private String requestRef;

    private Map<String, Object> payload = new HashMap<>();

    private String callbackUrl;

    // Getters and Setters
    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public UUID getConnectorId() {
        return connectorId;
    }

    public void setConnectorId(UUID connectorId) {
        this.connectorId = connectorId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public enum JobType {
        DELETE, EXPORT, AUDIT_PULL
    }

    public enum SubjectType {
        CUSTOMER, EMPLOYEE, VENDOR, OTHER
    }
}
