package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarEscalationTaskEntity;
import com.regulyn.dsar.entity.EscalationThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DsarEscalationTaskRepository extends JpaRepository<DsarEscalationTaskEntity, UUID> {

    Optional<DsarEscalationTaskEntity> findByTenantIdAndDsarIdAndThreshold(
        UUID tenantId, UUID dsarId, EscalationThreshold threshold);

    boolean existsByTenantIdAndDsarIdAndThreshold(UUID tenantId, UUID dsarId, EscalationThreshold threshold);

    java.util.List<DsarEscalationTaskEntity> findByTenantIdAndDsarIdOrderByReachedAtAsc(UUID tenantId, UUID dsarId);
}