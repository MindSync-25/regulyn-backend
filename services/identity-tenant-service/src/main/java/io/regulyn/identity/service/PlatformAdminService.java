package io.regulyn.identity.service;

import io.regulyn.identity.dto.TenantDetailDTO;
import io.regulyn.identity.dto.TenantResponse;
import io.regulyn.identity.dto.TenantSummaryDTO;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Platform admin service for cross-tenant operations.
 * Requires REGULYN_SUPER_ADMIN role.
 */
@Service
public class PlatformAdminService {

    private final TenantRepository tenantRepository;
    private final TenantLifecycleService tenantLifecycleService;

    public PlatformAdminService(TenantRepository tenantRepository,
                                TenantLifecycleService tenantLifecycleService) {
        this.tenantRepository = tenantRepository;
        this.tenantLifecycleService = tenantLifecycleService;
    }

    @Transactional(readOnly = true)
    public Page<TenantSummaryDTO> listTenants(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Tenant> tenants;
        if (status != null && !status.isBlank()) {
            tenants = tenantRepository.findByStatus(status, pageable);
        } else {
            tenants = tenantRepository.findAll(pageable);
        }
        
        return tenants.map(this::toSummaryDTO);
    }

    @Transactional(readOnly = true)
    public TenantDetailDTO getTenantDetail(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));
        
        return toDetailDTO(tenant);
    }

    @Transactional
    public TenantResponse activateTenant(UUID tenantId) {
        // Skip tenant context check for cross-tenant admin operations
        return tenantLifecycleService.activate(tenantId, true);
    }

    @Transactional
    public TenantResponse suspendTenant(UUID tenantId) {
        // Skip tenant context check for cross-tenant admin operations
        return tenantLifecycleService.suspend(tenantId, null, true);
    }

    @Transactional
    public TenantResponse resumeTenant(UUID tenantId) {
        // Skip tenant context check for cross-tenant admin operations
        return tenantLifecycleService.resume(tenantId, true);
    }

    private TenantSummaryDTO toSummaryDTO(Tenant tenant) {
        return new TenantSummaryDTO(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getPlanCode(),
                tenant.getCreatedAt()
        );
    }

    private TenantDetailDTO toDetailDTO(Tenant tenant) {
        TenantDetailDTO dto = new TenantDetailDTO();
        dto.setTenantId(tenant.getTenantId());
        dto.setName(tenant.getName());
        dto.setStatus(tenant.getStatus());
        dto.setPlanCode(tenant.getPlanCode());
        dto.setCreatedAt(tenant.getCreatedAt());
        dto.setUpdatedAt(tenant.getUpdatedAt());
        dto.setAdminBootstrappedAt(tenant.getAdminBootstrappedAt());
        dto.setHasAdminBootstrap(tenant.getAdminBootstrapUserId() != null);
        return dto;
    }
}
