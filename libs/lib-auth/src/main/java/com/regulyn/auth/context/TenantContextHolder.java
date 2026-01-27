package com.regulyn.auth.context;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class TenantContextHolder {
  
  private static final ThreadLocal<TenantContext> contextHolder = new ThreadLocal<>();
  
  public static void setContext(TenantContext context) {
    contextHolder.set(context);
  }
  
  public static TenantContext getContext() {
    TenantContext context = contextHolder.get();
    if (context == null) {
      // Return empty context with safe defaults
      context = new TenantContext();
      context.setRoles(Collections.emptySet());
    }
    return context;
  }
  
  public static UUID getTenantId() {
    return getContext().getTenantId();
  }
  
  public static UUID getUserId() {
    return getContext().getUserId();
  }
  
  public static Set<String> getRoles() {
    return getContext().getRoles();
  }
  
  public static String getRequestId() {
    return getContext().getRequestId();
  }
  
  public static String getTraceId() {
    return getContext().getTraceId();
  }
  
  public static void clear() {
    contextHolder.remove();
  }
}
