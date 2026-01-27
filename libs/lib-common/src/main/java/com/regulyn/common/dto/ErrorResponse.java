package com.regulyn.common.dto;

import java.time.Instant;

public class ErrorResponse {
  private Instant timestamp;
  private int status;
  private String error;
  private String message;
  private String path;
  private String requestId;
  private String tenantId;

  public ErrorResponse() {
    this.timestamp = Instant.now();
  }

  public ErrorResponse(int status, String error, String message, String path, String requestId, String tenantId) {
    this.timestamp = Instant.now();
    this.status = status;
    this.error = error;
    this.message = message;
    this.path = path;
    this.requestId = requestId;
    this.tenantId = tenantId;
  }

  public Instant getTimestamp() { return timestamp; }
  public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
  
  public int getStatus() { return status; }
  public void setStatus(int status) { this.status = status; }
  
  public String getError() { return error; }
  public void setError(String error) { this.error = error; }
  
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  
  public String getPath() { return path; }
  public void setPath(String path) { this.path = path; }
  
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  
  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
