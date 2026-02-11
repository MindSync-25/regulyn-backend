package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.AgeThresholdRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgeThresholdRuleRepository extends JpaRepository<AgeThresholdRuleEntity, UUID> {

    Optional<AgeThresholdRuleEntity> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    Optional<AgeThresholdRuleEntity> findByTenantIdAndRegionCountryCodeAndRegionStateCode(
            UUID tenantId,
            String regionCountryCode,
            String regionStateCode
    );

    List<AgeThresholdRuleEntity> findByTenantIdOrderByIsDefaultDescRegionCountryCodeAscRegionStateCodeAsc(UUID tenantId);
}
