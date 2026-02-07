package com.regulyn.retention.integration;

import java.util.Map;

public class CreateArtifactRequest {
    private String userId;
    private String eventType;
    private String evidenceType;
    private String description;
    private Map<String, Object> metadata;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}