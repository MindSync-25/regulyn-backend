package com.regulyn.guardian.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "child_majority_transitions", schema = "children")
public class ChildMajorityTransitionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "region_country_code", nullable = false, length = 2)
    private String regionCountryCode;

    @Column(name = "region_state_code", length = 10)
    private String regionStateCode;

    @Column(name = "threshold_age_years", nullable = false)
    private Short thresholdAgeYears;

    @Column(name = "majority_date", nullable = false)
    private LocalDate majorityDate;

    @Column(name = "transition_status", nullable = false, length = 40)
    private String transitionStatus;

    @Column(name = "last_notification_status", length = 40)
    private String lastNotificationStatus;

    @Column(name = "last_notification_error")
    private String lastNotificationError;

    @Column(name = "last_notification_at")
    private Instant lastNotificationAt;

    @Column(name = "adult_consent_receipt_ref", length = 200)
    private String adultConsentReceiptRef;

    @Column(name = "adult_consent_receipt_sha256", length = 64)
    private String adultConsentReceiptSha256;

    @Column(name = "adult_consent_recorded_at")
    private Instant adultConsentRecordedAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastEvaluatedAt == null) {
            lastEvaluatedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getRegionCountryCode() {
        return regionCountryCode;
    }

    public void setRegionCountryCode(String regionCountryCode) {
        this.regionCountryCode = regionCountryCode;
    }

    public String getRegionStateCode() {
        return regionStateCode;
    }

    public void setRegionStateCode(String regionStateCode) {
        this.regionStateCode = regionStateCode;
    }

    public Short getThresholdAgeYears() {
        return thresholdAgeYears;
    }

    public void setThresholdAgeYears(Short thresholdAgeYears) {
        this.thresholdAgeYears = thresholdAgeYears;
    }

    public LocalDate getMajorityDate() {
        return majorityDate;
    }

    public void setMajorityDate(LocalDate majorityDate) {
        this.majorityDate = majorityDate;
    }

    public String getTransitionStatus() {
        return transitionStatus;
    }

    public void setTransitionStatus(String transitionStatus) {
        this.transitionStatus = transitionStatus;
    }

    public String getLastNotificationStatus() {
        return lastNotificationStatus;
    }

    public void setLastNotificationStatus(String lastNotificationStatus) {
        this.lastNotificationStatus = lastNotificationStatus;
    }

    public String getLastNotificationError() {
        return lastNotificationError;
    }

    public void setLastNotificationError(String lastNotificationError) {
        this.lastNotificationError = lastNotificationError;
    }

    public Instant getLastNotificationAt() {
        return lastNotificationAt;
    }

    public void setLastNotificationAt(Instant lastNotificationAt) {
        this.lastNotificationAt = lastNotificationAt;
    }

    public String getAdultConsentReceiptRef() {
        return adultConsentReceiptRef;
    }

    public void setAdultConsentReceiptRef(String adultConsentReceiptRef) {
        this.adultConsentReceiptRef = adultConsentReceiptRef;
    }

    public String getAdultConsentReceiptSha256() {
        return adultConsentReceiptSha256;
    }

    public void setAdultConsentReceiptSha256(String adultConsentReceiptSha256) {
        this.adultConsentReceiptSha256 = adultConsentReceiptSha256;
    }

    public Instant getAdultConsentRecordedAt() {
        return adultConsentRecordedAt;
    }

    public void setAdultConsentRecordedAt(Instant adultConsentRecordedAt) {
        this.adultConsentRecordedAt = adultConsentRecordedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Instant lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
