package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TenantHealthSummaryDTO {
    private UUID tenantId;
    private ServiceStatus status;
    private List<TenantServiceStatusDTO> services;
    private Integer connectorFailures;
    private Integer notificationFailures;
    private Long evidenceUsageBytes;
    private Double evidenceUsagePct;
    private Instant lastUpdated;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public List<TenantServiceStatusDTO> getServices() {
        return services;
    }

    public void setServices(List<TenantServiceStatusDTO> services) {
        this.services = services;
    }

    public Integer getConnectorFailures() {
        return connectorFailures;
    }

    public void setConnectorFailures(Integer connectorFailures) {
        this.connectorFailures = connectorFailures;
    }

    public Integer getNotificationFailures() {
        return notificationFailures;
    }

    public void setNotificationFailures(Integer notificationFailures) {
        this.notificationFailures = notificationFailures;
    }

    public Long getEvidenceUsageBytes() {
        return evidenceUsageBytes;
    }

    public void setEvidenceUsageBytes(Long evidenceUsageBytes) {
        this.evidenceUsageBytes = evidenceUsageBytes;
    }

    public Double getEvidenceUsagePct() {
        return evidenceUsagePct;
    }

    public void setEvidenceUsagePct(Double evidenceUsagePct) {
        this.evidenceUsagePct = evidenceUsagePct;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
