package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.Vendor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateVendorRequest {

    @NotBlank(message = "vendorName is required")
    private String vendorName;

    @NotNull(message = "vendorType is required")
    private Vendor.VendorType vendorType;

    @Email(message = "contactEmail must be valid email")
    private String contactEmail;

    @NotBlank(message = "country is required")
    private String country;

    @NotNull(message = "hostingRegion is required")
    private Vendor.HostingRegion hostingRegion;

    @NotNull(message = "riskLevel is required")
    private Vendor.RiskLevel riskLevel;

    private Map<String, Object> metadata;

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

    public Vendor.RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Vendor.RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
