package com.regulyn.auth.client;

import java.util.List;
import java.util.UUID;

public class ValidationResult {
    private boolean valid;
    private UUID tenantId;
    private UUID userId;
    private List<String> roles;

    public ValidationResult() {}

    public ValidationResult(boolean valid, UUID tenantId, UUID userId, List<String> roles) {
        this.valid = valid;
        this.tenantId = tenantId;
        this.userId = userId;
        this.roles = roles;
    }

    public static ValidationResult invalid() {
        return new ValidationResult(false, null, null, null);
    }

    public static ValidationResult valid(UUID tenantId, UUID userId, List<String> roles) {
        return new ValidationResult(true, tenantId, userId, roles);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
