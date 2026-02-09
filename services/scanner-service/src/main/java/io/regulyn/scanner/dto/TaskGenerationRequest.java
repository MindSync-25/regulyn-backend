package io.regulyn.scanner.dto;

import java.util.List;
import java.util.UUID;

public class TaskGenerationRequest {

    private List<String> kinds;
    private UUID defaultOwnerUserId;
    private String defaultOwnerEmail;
    private boolean forceReopenClosed = false;

    public List<String> getKinds() {
        return kinds;
    }

    public void setKinds(List<String> kinds) {
        this.kinds = kinds;
    }

    public UUID getDefaultOwnerUserId() {
        return defaultOwnerUserId;
    }

    public void setDefaultOwnerUserId(UUID defaultOwnerUserId) {
        this.defaultOwnerUserId = defaultOwnerUserId;
    }

    public String getDefaultOwnerEmail() {
        return defaultOwnerEmail;
    }

    public void setDefaultOwnerEmail(String defaultOwnerEmail) {
        this.defaultOwnerEmail = defaultOwnerEmail;
    }

    public boolean isForceReopenClosed() {
        return forceReopenClosed;
    }

    public void setForceReopenClosed(boolean forceReopenClosed) {
        this.forceReopenClosed = forceReopenClosed;
    }
}
