package com.regulyn.dsar.model;

public class DsarResponse {
  private String requestId;
  private String status;

  public DsarResponse(String requestId, String status) {
    this.requestId = requestId;
    this.status = status;
  }

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
