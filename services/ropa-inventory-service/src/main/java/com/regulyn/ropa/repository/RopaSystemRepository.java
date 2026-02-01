package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaSystemRepository extends JpaRepository<RopaSystem, UUID>, JpaSpecificationExecutor<RopaSystem> {

    List<RopaSystem> findByTenantId(UUID tenantId);

    List<RopaSystem> findByTenantIdAndSystemType(UUID tenantId, RopaSystem.SystemType systemType);

    List<RopaSystem> findByTenantIdAndCriticality(UUID tenantId, RopaSystem.Criticality criticality);

    List<RopaSystem> findByTenantIdAndEnabled(UUID tenantId, Boolean enabled);
}
