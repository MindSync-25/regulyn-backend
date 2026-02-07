package com.regulyn.retention.connector;

import java.util.UUID;

public class StartDeletionJobRequest {
    private UUID tenantId;
    private UUID deletionId;
    private String systemKey;
    private String subjectType;
    private String subjectRef;
    private String entityType;
    private String requestIdempotencyKey;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public String getSystemKey() { return systemKey; }
    public void setSystemKey(String systemKey) { this.systemKey = systemKey; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getRequestIdempotencyKey() { return requestIdempotencyKey; }
    public void setRequestIdempotencyKey(String requestIdempotencyKey) { this.requestIdempotencyKey = requestIdempotencyKey; }
}
