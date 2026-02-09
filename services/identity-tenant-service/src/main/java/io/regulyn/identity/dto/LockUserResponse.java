package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

public class LockUserResponse {
    private UUID userId;
    private Instant lockedAt;
    private String lockedReason;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getLockedReason() {
        return lockedReason;
    }

    public void setLockedReason(String lockedReason) {
        this.lockedReason = lockedReason;
    }
}
