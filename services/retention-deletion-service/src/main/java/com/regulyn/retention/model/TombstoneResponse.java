package com.regulyn.retention.model;

import java.time.Instant;
import java.util.UUID;

public class TombstoneResponse {
    private UUID tombstoneId;
    private UUID tenantId;
    private String subjectType;
    private String subjectRef;
    private String status;
    private String reason;
    private UUID createdFromDeletionId;
    private UUID createdArtifactId;
    private UUID removedArtifactId;
    private Instant removedAt;

    public UUID getTombstoneId() { return tombstoneId; }
    public void setTombstoneId(UUID tombstoneId) { this.tombstoneId = tombstoneId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getCreatedFromDeletionId() { return createdFromDeletionId; }
    public void setCreatedFromDeletionId(UUID createdFromDeletionId) { this.createdFromDeletionId = createdFromDeletionId; }

    public UUID getCreatedArtifactId() { return createdArtifactId; }
    public void setCreatedArtifactId(UUID createdArtifactId) { this.createdArtifactId = createdArtifactId; }

    public UUID getRemovedArtifactId() { return removedArtifactId; }
    public void setRemovedArtifactId(UUID removedArtifactId) { this.removedArtifactId = removedArtifactId; }

    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }
}