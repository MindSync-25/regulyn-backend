package io.regulyn.identity.dto;

import java.time.Instant;

public class ApiKeyCreateRequest {
    private String name;
    private Instant expiresAt;
    private Integer expiresInDays;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getExpiresInDays() {
        return expiresInDays;
    }

    public void setExpiresInDays(Integer expiresInDays) {
        this.expiresInDays = expiresInDays;
    }
}
