package io.regulyn.identity.dto;

public class SuspendTenantRequest {
    private String reason;

    public SuspendTenantRequest() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
