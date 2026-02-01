package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaActivityVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RopaActivityVersionRepository extends JpaRepository<RopaActivityVersion, UUID>, JpaSpecificationExecutor<RopaActivityVersion> {

    List<RopaActivityVersion> findByTenantIdAndActivityId(UUID tenantId, UUID activityId);

    Optional<RopaActivityVersion> findByTenantIdAndActivityIdAndVersionNumber(UUID tenantId, UUID activityId, Integer versionNumber);

    Optional<RopaActivityVersion> findFirstByTenantIdAndActivityIdOrderByVersionNumberDesc(UUID tenantId, UUID activityId);

    List<RopaActivityVersion> findByTenantIdAndStatus(UUID tenantId, RopaActivityVersion.Status status);

    Page<RopaActivityVersion> findByTenantId(UUID tenantId, Pageable pageable);
}
