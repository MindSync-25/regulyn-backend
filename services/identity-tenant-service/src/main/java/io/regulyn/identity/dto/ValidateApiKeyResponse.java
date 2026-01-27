package io.regulyn.identity.dto;

import java.util.List;
import java.util.UUID;

public class ValidateApiKeyResponse {
    private boolean valid;
    private UUID tenantId;
    private UUID userId;
    private List<String> roles;

    public ValidateApiKeyResponse() {}

    public ValidateApiKeyResponse(boolean valid, UUID tenantId, UUID userId, List<String> roles) {
        this.valid = valid;
        this.tenantId = tenantId;
        this.userId = userId;
        this.roles = roles;
    }

    public static ValidateApiKeyResponse invalid() {
        return new ValidateApiKeyResponse(false, null, null, null);
    }

    public static ValidateApiKeyResponse valid(UUID tenantId, UUID userId, List<String> roles) {
        return new ValidateApiKeyResponse(true, tenantId, userId, roles);
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
