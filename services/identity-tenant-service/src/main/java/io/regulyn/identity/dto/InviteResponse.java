package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InviteResponse {

    private UUID inviteId;
    private UUID tenantId;
    private String email;
    private List<String> roles;
    private Instant expiresAt;
    private String token;

    public UUID getInviteId() {
        return inviteId;
    }

    public void setInviteId(UUID inviteId) {
        this.inviteId = inviteId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
