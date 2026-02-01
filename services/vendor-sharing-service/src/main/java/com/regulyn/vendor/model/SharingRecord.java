package com.regulyn.vendor.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sharing_records", schema = "vendor")
public class SharingRecord {

    public enum LawfulBasis {
        CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTERESTS, PUBLIC_TASK, LEGITIMATE_INTERESTS, OTHER
    }

    public enum DataCategory {
        PII, FINANCIAL, HEALTH, BIOMETRIC, CHILD_DATA, EMPLOYEE_DATA, DEVICE, OTHER
    }

    public enum Frequency {
        ONE_TIME, ONGOING, PER_REQUEST
    }

    public enum SharingStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }

    @Id
    @Column(name = "sharing_id")
    private UUID sharingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "system_id")
    private UUID systemId;

    @Column(name = "sharing_purpose", nullable = false)
    private String sharingPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "lawful_basis", nullable = false)
    private LawfulBasis lawfulBasis;

    @Column(name = "data_categories", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> dataCategories;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;

    @Column(name = "transfer_cross_border", nullable = false)
    private Boolean transferCrossBorder = false;

    @Column(name = "transfer_to_regions", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> transferToRegions;

    @Column(name = "transfer_notes")
    private String transferNotes;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SharingStatus status = SharingStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (sharingId == null) {
            sharingId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getSharingId() { return sharingId; }
    public void setSharingId(UUID sharingId) { this.sharingId = sharingId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID activityId) { this.activityId = activityId; }

    public UUID getSystemId() { return systemId; }
    public void setSystemId(UUID systemId) { this.systemId = systemId; }

    public String getSharingPurpose() { return sharingPurpose; }
    public void setSharingPurpose(String sharingPurpose) { this.sharingPurpose = sharingPurpose; }

    public LawfulBasis getLawfulBasis() { return lawfulBasis; }
    public void setLawfulBasis(LawfulBasis lawfulBasis) { this.lawfulBasis = lawfulBasis; }

    public List<String> getDataCategories() { return dataCategories; }
    public void setDataCategories(List<String> dataCategories) { this.dataCategories = dataCategories; }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

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

    public SharingStatus getStatus() { return status; }
    public void setStatus(SharingStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
