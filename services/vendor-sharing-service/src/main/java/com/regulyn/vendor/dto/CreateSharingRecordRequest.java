package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.SharingRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateSharingRecordRequest {

    @NotNull(message = "vendorId is required")
    private UUID vendorId;

    private UUID activityId;
    private UUID systemId;

    @NotBlank(message = "sharingPurpose is required")
    private String sharingPurpose;

    @NotNull(message = "lawfulBasis is required")
    private SharingRecord.LawfulBasis lawfulBasis;

    @NotEmpty(message = "dataCategories is required")
    private List<String> dataCategories;

    @NotNull(message = "frequency is required")
    private SharingRecord.Frequency frequency;

    private Boolean transferCrossBorder = false;
    private List<String> transferToRegions;
    private String transferNotes;
    private Instant startAt;
    private Instant endAt;
    private Map<String, Object> metadata;

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

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
