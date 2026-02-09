package io.regulyn.identity.repository;

import io.regulyn.identity.entity.TenantPlanLimits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantPlanLimitsRepository extends JpaRepository<TenantPlanLimits, UUID> {
    Optional<TenantPlanLimits> findByTenantId(UUID tenantId);
}
