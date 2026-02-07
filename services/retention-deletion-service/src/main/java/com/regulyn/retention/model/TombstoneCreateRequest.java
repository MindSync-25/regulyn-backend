package com.regulyn.retention.model;

import java.util.UUID;

public class TombstoneCreateRequest {
    private String subjectType;
    private String subjectRef;
    private String reason;
    private UUID createdFromDeletionId;

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getCreatedFromDeletionId() { return createdFromDeletionId; }
    public void setCreatedFromDeletionId(UUID createdFromDeletionId) { this.createdFromDeletionId = createdFromDeletionId; }
}