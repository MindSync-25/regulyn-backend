package com.regulyn.dsar.model;

public class DsarRequest {
  private String userId;
  private String requestType;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  
  public String getRequestType() { return requestType; }
  public void setRequestType(String requestType) { this.requestType = requestType; }
}
