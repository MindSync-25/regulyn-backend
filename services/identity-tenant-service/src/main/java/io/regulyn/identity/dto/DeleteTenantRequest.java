package io.regulyn.identity.dto;

public class DeleteTenantRequest {
    private String reason;

    public DeleteTenantRequest() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
