package com.regulyn.dsar.client;

import java.util.UUID;

public class CreateBundleResponse {
    private UUID bundleId;
    private String bundleHash;
    private String status;

    public CreateBundleResponse() {
    }

    // Getters and setters
    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public String getBundleHash() {
        return bundleHash;
    }

    public void setBundleHash(String bundleHash) {
        this.bundleHash = bundleHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
