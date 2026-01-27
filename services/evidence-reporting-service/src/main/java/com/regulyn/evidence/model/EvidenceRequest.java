package com.regulyn.evidence.model;

import java.util.Map;

public class EvidenceRequest {
  private String userId;
  private String eventType;
  private Map<String, Object> metadata;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  
  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
