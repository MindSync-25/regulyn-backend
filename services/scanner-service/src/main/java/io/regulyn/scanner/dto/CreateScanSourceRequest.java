package io.regulyn.scanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public class CreateScanSourceRequest {

    @NotBlank(message = "sourceName is required")
    private String sourceName;

    @NotNull(message = "systemId is required")
    private UUID systemId;

    @NotNull(message = "sourceType is required")
    private SourceType sourceType;

    @NotNull(message = "status is required")
    private SourceStatus status;

    private String baseUrl;

    @NotNull(message = "authType is required")
    private AuthType authType = AuthType.NONE;

    private String authRef;

    private Map<String, Object> metadata;

    // Enums
    public enum SourceType {
        MOCK,
        HTTP_DISCOVERY,
        WEBSITE
    }

    public enum SourceStatus {
        ACTIVE,
        DISABLED
    }

    public enum AuthType {
        NONE,
        API_KEY,
        BEARER
    }

    // Getters and Setters
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getAuthRef() {
        return authRef;
    }

    public void setAuthRef(String authRef) {
        this.authRef = authRef;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
