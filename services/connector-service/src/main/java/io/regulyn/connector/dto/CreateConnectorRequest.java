package io.regulyn.connector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

public class CreateConnectorRequest {

    @NotBlank(message = "connectorName is required")
    private String connectorName;

    @NotNull(message = "connectorType is required")
    private ConnectorType connectorType;

    @NotNull(message = "status is required")
    private ConnectorStatus status;

    private String baseUrl;

    @NotNull(message = "authType is required")
    private AuthType authType;

    private String authRef;

    private Map<String, Object> metadata = new HashMap<>();

    // Getters and Setters
    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public ConnectorStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectorStatus status) {
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

    public enum ConnectorType {
        MOCK, WEBHOOK_HTTP, DB_STUB, SAAS_STUB
    }

    public enum ConnectorStatus {
        ACTIVE, DISABLED
    }

    public enum AuthType {
        NONE, API_KEY, BEARER
    }
}
