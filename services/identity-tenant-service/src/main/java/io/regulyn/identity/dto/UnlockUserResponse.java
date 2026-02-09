package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

public class UnlockUserResponse {
    private UUID userId;
    private Instant unlockedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Instant unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
