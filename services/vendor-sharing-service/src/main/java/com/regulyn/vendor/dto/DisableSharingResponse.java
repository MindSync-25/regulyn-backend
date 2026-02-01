package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.SharingRecord;

import java.util.UUID;

public class DisableSharingResponse {
    private UUID sharingId;
    private Boolean enabled;
    private SharingRecord.SharingStatus status;
    private String message;

    public UUID getSharingId() { return sharingId; }
    public void setSharingId(UUID sharingId) { this.sharingId = sharingId; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public SharingRecord.SharingStatus getStatus() { return status; }
    public void setStatus(SharingRecord.SharingStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
