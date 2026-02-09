package io.regulyn.identity.repository;

import io.regulyn.identity.entity.TenantMonthlyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantMonthlyUsageRepository extends JpaRepository<TenantMonthlyUsage, UUID> {
    Optional<TenantMonthlyUsage> findByTenantIdAndYearMonth(UUID tenantId, Integer yearMonth);
}
