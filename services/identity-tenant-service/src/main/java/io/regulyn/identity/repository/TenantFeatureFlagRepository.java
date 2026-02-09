package io.regulyn.identity.repository;

import io.regulyn.identity.entity.TenantFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantFeatureFlagRepository extends JpaRepository<TenantFeatureFlag, UUID> {
    Optional<TenantFeatureFlag> findByTenantIdAndFlagKey(UUID tenantId, String flagKey);

    List<TenantFeatureFlag> findByTenantId(UUID tenantId);
}
