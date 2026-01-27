package com.regulyn.auth.context;

import java.util.Set;
import java.util.UUID;

public class TenantContext {
  private UUID tenantId;
  private UUID userId;
  private Set<String> roles;
  private String traceId;
  private String requestId;

  public TenantContext() {}

  public TenantContext(UUID tenantId, UUID userId, Set<String> roles, String traceId, String requestId) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.roles = roles;
    this.traceId = traceId;
    this.requestId = requestId;
  }

  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  
  public Set<String> getRoles() { return roles; }
  public void setRoles(Set<String> roles) { this.roles = roles; }
  
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
}
