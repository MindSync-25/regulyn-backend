package com.regulyn.auth.apikey;

import java.util.UUID;

public interface ApiKeyValidator {
  ApiKeyValidationResult validate(String apiKeyHash);
  
  class ApiKeyValidationResult {
    private final boolean valid;
    private final UUID tenantId;
    private final String name;
    
    public ApiKeyValidationResult(boolean valid, UUID tenantId, String name) {
      this.valid = valid;
      this.tenantId = tenantId;
      this.name = name;
    }
    
    public boolean isValid() { return valid; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    
    public static ApiKeyValidationResult invalid() {
      return new ApiKeyValidationResult(false, null, null);
    }
    
    public static ApiKeyValidationResult valid(UUID tenantId, String name) {
      return new ApiKeyValidationResult(true, tenantId, name);
    }
  }
}
