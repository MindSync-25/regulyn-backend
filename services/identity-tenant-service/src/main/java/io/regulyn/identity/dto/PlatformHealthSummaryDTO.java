package io.regulyn.identity.dto;

import java.util.List;

public class PlatformHealthSummaryDTO {
    private ServiceStatus platformStatus;
    private Long tenantsUp;
    private Long tenantsDown;
    private Long totalTenants;
    private List<PlatformIncidentDTO> incidents;

    public ServiceStatus getPlatformStatus() {
        return platformStatus;
    }

    public void setPlatformStatus(ServiceStatus platformStatus) {
        this.platformStatus = platformStatus;
    }

    public Long getTenantsUp() {
        return tenantsUp;
    }

    public void setTenantsUp(Long tenantsUp) {
        this.tenantsUp = tenantsUp;
    }

    public Long getTenantsDown() {
        return tenantsDown;
    }

    public void setTenantsDown(Long tenantsDown) {
        this.tenantsDown = tenantsDown;
    }

    public Long getTotalTenants() {
        return totalTenants;
    }

    public void setTotalTenants(Long totalTenants) {
        this.totalTenants = totalTenants;
    }

    public List<PlatformIncidentDTO> getIncidents() {
        return incidents;
    }

    public void setIncidents(List<PlatformIncidentDTO> incidents) {
        this.incidents = incidents;
    }
}
