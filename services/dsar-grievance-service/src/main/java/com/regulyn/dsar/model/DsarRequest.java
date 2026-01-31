package com.regulyn.dsar.model;

public class DsarRequest {
  private String userId;
  private String requestType;
  private String requesterEmail;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  
  public String getRequestType() { return requestType; }
  public void setRequestType(String requestType) { this.requestType = requestType; }
  
  public String getRequesterEmail() { return requesterEmail; }
  public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
}
