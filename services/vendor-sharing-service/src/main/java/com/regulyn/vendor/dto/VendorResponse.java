package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.Vendor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class VendorResponse {
    private UUID vendorId;
    private String vendorName;
    private Vendor.VendorType vendorType;
    private String contactEmail;
    private String country;
    private Vendor.HostingRegion hostingRegion;
    private Boolean enabled;
    private Vendor.RiskLevel riskLevel;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public Vendor.VendorType getVendorType() { return vendorType; }
    public void setVendorType(Vendor.VendorType vendorType) { this.vendorType = vendorType; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Vendor.HostingRegion getHostingRegion() { return hostingRegion; }
    public void setHostingRegion(Vendor.HostingRegion hostingRegion) { this.hostingRegion = hostingRegion; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Vendor.RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Vendor.RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
