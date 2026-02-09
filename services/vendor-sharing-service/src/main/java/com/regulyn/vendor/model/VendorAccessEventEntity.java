package com.regulyn.vendor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "vendor_access_events", schema = "vendor")
public class VendorAccessEventEntity {

    @Id
    @Column(name = "access_event_id")
    private UUID accessEventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "source")
    private String source;

    @Column(name = "access_type", nullable = false)
    private String accessType;

    @Column(name = "subject_ref")
    private String subjectRef;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "data_categories", columnDefinition = "text[]")
    private List<String> dataCategories;

    @Column(name = "purpose_ref")
    private String purposeRef;

    @Column(name = "purpose_version")
    private Long purposeVersion;

    @Column(name = "accessed_at", nullable = false)
    private OffsetDateTime accessedAt;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "raw_payload_hash", nullable = false)
    private String rawPayloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload_ref", columnDefinition = "jsonb")
    private Map<String, Object> rawPayloadRef;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (accessEventId == null) {
            accessEventId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }

    public UUID getAccessEventId() {
        return accessEventId;
    }

    public void setAccessEventId(UUID accessEventId) {
        this.accessEventId = accessEventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public void setSubjectRef(String subjectRef) {
        this.subjectRef = subjectRef;
    }

    public List<String> getDataCategories() {
        return dataCategories;
    }

    public void setDataCategories(List<String> dataCategories) {
        this.dataCategories = dataCategories;
    }

    public String getPurposeRef() {
        return purposeRef;
    }

    public void setPurposeRef(String purposeRef) {
        this.purposeRef = purposeRef;
    }

    public Long getPurposeVersion() {
        return purposeVersion;
    }

    public void setPurposeVersion(Long purposeVersion) {
        this.purposeVersion = purposeVersion;
    }

    public OffsetDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(OffsetDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRawPayloadHash() {
        return rawPayloadHash;
    }

    public void setRawPayloadHash(String rawPayloadHash) {
        this.rawPayloadHash = rawPayloadHash;
    }

    public Map<String, Object> getRawPayloadRef() {
        return rawPayloadRef;
    }

    public void setRawPayloadRef(Map<String, Object> rawPayloadRef) {
        this.rawPayloadRef = rawPayloadRef;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
