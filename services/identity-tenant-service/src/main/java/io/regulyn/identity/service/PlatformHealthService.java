package io.regulyn.identity.service;

import io.regulyn.identity.dto.PlatformHealthSummaryDTO;
import io.regulyn.identity.dto.ServiceStatus;
import io.regulyn.identity.dto.TenantHealthSummaryDTO;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformHealthService {

    private final TenantRepository tenantRepository;

    public PlatformHealthService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public PlatformHealthSummaryDTO getSummary() {
        PlatformHealthSummaryDTO summary = new PlatformHealthSummaryDTO();
        long totalTenants = tenantRepository.count();
        long tenantsUp = tenantRepository.countByStatus("ACTIVE");
        long tenantsDown = tenantRepository.countByStatus("SUSPENDED") + tenantRepository.countByStatus("DELETED");

        summary.setPlatformStatus(ServiceStatus.UNKNOWN);
        summary.setTotalTenants(totalTenants);
        summary.setTenantsUp(tenantsUp);
        summary.setTenantsDown(tenantsDown);
        summary.setIncidents(Collections.emptyList());
        return summary;
    }

    public Page<TenantHealthSummaryDTO> listTenantHealth(Pageable pageable) {
        Page<Tenant> page = tenantRepository.findAll(pageable);
        List<TenantHealthSummaryDTO> items = page.getContent().stream()
            .map(this::toSummary)
            .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    public TenantHealthSummaryDTO getTenantHealth(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));
        return toSummary(tenant);
    }

    private TenantHealthSummaryDTO toSummary(Tenant tenant) {
        TenantHealthSummaryDTO dto = new TenantHealthSummaryDTO();
        dto.setTenantId(tenant.getTenantId());
        dto.setStatus(mapStatus(tenant.getStatus()));
        dto.setServices(Collections.emptyList());
        dto.setConnectorFailures(0);
        dto.setNotificationFailures(0);
        dto.setEvidenceUsageBytes(null);
        dto.setEvidenceUsagePct(null);
        dto.setLastUpdated(tenant.getUpdatedAt());
        return dto;
    }

    private ServiceStatus mapStatus(String status) {
        if (status == null) {
            return ServiceStatus.UNKNOWN;
        }
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> ServiceStatus.UP;
            case "SUSPENDED", "DELETED" -> ServiceStatus.DOWN;
            default -> ServiceStatus.UNKNOWN;
        };
    }
}
