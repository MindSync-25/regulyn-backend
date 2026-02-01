package com.regulyn.employee.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "hr_purposes", schema = "employee")
public class HRPurpose {

    public enum PurposeKey {
        PAYROLL, BENEFITS, RECRUITMENT, PERFORMANCE, SECURITY, COMPLIANCE, OTHER
    }

    public enum LawfulBasis {
        CONTRACT, LEGAL_OBLIGATION, CONSENT, LEGITIMATE_INTERESTS, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hr_purpose_id")
    private UUID hrPurposeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose_key", nullable = false)
    private PurposeKey purposeKey;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "lawful_basis", nullable = false)
    private LawfulBasis lawfulBasis;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "sensitive", nullable = false)
    private Boolean sensitive = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (sensitive == null) {
            sensitive = false;
        }
    }

    // Getters and setters
    public UUID getHrPurposeId() {
        return hrPurposeId;
    }

    public void setHrPurposeId(UUID hrPurposeId) {
        this.hrPurposeId = hrPurposeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public PurposeKey getPurposeKey() {
        return purposeKey;
    }

    public void setPurposeKey(PurposeKey purposeKey) {
        this.purposeKey = purposeKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LawfulBasis getLawfulBasis() {
        return lawfulBasis;
    }

    public void setLawfulBasis(LawfulBasis lawfulBasis) {
        this.lawfulBasis = lawfulBasis;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
