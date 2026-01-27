package io.regulyn.identity.repository;

import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.Tenant.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    Optional<Tenant> findByTenantIdAndStatus(UUID tenantId, TenantStatus status);
    
    List<Tenant> findByStatus(TenantStatus status);
}
