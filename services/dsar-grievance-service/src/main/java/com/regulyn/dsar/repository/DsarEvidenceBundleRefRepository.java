package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarEvidenceBundleRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DsarEvidenceBundleRefRepository extends JpaRepository<DsarEvidenceBundleRefEntity, UUID> {

    Optional<DsarEvidenceBundleRefEntity> findByTenantIdAndDsarIdAndCloseEventId(
        UUID tenantId, UUID dsarId, UUID closeEventId);
}