package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.SharingRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SharingRecordResponse {
    private UUID sharingId;
    private UUID vendorId;
    private UUID activityId;
    private UUID systemId;
    private String sharingPurpose;
    private SharingRecord.LawfulBasis lawfulBasis;
    private List<String> dataCategories;
    private SharingRecord.Frequency frequency;
    private Boolean transferCrossBorder;
    private List<String> transferToRegions;
    private String transferNotes;
    private Instant startAt;
    private Instant endAt;
    private Boolean enabled;
    private Map<String, Object> metadata;
    private SharingRecord.SharingStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getSharingId() { return sharingId; }
    public void setSharingId(UUID sharingId) { this.sharingId = sharingId; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID activityId) { this.activityId = activityId; }

    public UUID getSystemId() { return systemId; }
    public void setSystemId(UUID systemId) { this.systemId = systemId; }

    public String getSharingPurpose() { return sharingPurpose; }
    public void setSharingPurpose(String sharingPurpose) { this.sharingPurpose = sharingPurpose; }

    public SharingRecord.LawfulBasis getLawfulBasis() { return lawfulBasis; }
    public void setLawfulBasis(SharingRecord.LawfulBasis lawfulBasis) { this.lawfulBasis = lawfulBasis; }

    public List<String> getDataCategories() { return dataCategories; }
    public void setDataCategories(List<String> dataCategories) { this.dataCategories = dataCategories; }

    public SharingRecord.Frequency getFrequency() { return frequency; }
    public void setFrequency(SharingRecord.Frequency frequency) { this.frequency = frequency; }

    public Boolean getTransferCrossBorder() { return transferCrossBorder; }
    public void setTransferCrossBorder(Boolean transferCrossBorder) { this.transferCrossBorder = transferCrossBorder; }

    public List<String> getTransferToRegions() { return transferToRegions; }
    public void setTransferToRegions(List<String> transferToRegions) { this.transferToRegions = transferToRegions; }

    public String getTransferNotes() { return transferNotes; }
    public void setTransferNotes(String transferNotes) { this.transferNotes = transferNotes; }

    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }

    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public SharingRecord.SharingStatus getStatus() { return status; }
    public void setStatus(SharingRecord.SharingStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
