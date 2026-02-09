package io.regulyn.identity.service;

import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class TenantWriteGuard {

    private final TenantRepository tenantRepository;

    public TenantWriteGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public void guardWrite(UUID tenantId, String operationName) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        if (Boolean.TRUE.equals(tenant.getReadOnly())) {
            String reason = tenant.getReadOnlyReason() != null ? tenant.getReadOnlyReason() : "TENANT_READ_ONLY";
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
        }
    }
}
