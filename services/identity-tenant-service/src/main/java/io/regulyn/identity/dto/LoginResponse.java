package io.regulyn.identity.dto;

import java.util.Set;

public class LoginResponse {
    private String token;
    private String tenantId;
    private String userId;
    private String email;
    private Set<String> roles;

    public LoginResponse() {}

    public LoginResponse(String token, String tenantId, String userId, String email, Set<String> roles) {
        this.token = token;
        this.tenantId = tenantId;
        this.userId = userId;
        this.email = email;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
