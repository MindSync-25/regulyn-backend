package com.regulyn.vendor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorAccessTelemetryEventIngestDto {

    private UUID vendorId;
    private String systemName;
    private String source;
    private String accessType;
    private String subjectRef;
    private List<String> dataCategories;
    private String purposeRef;
    private Long purposeVersion;
    private OffsetDateTime accessedAt;
    private String correlationId;
    private String actorType;
    private String actorId;
    private String ip;
    private String userAgent;
    private String result;
    private String rawPayloadHash;
    private Map<String, Object> rawPayloadRef;

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
}
