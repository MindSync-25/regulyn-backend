package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarAttachmentEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DsarAttachmentRepository extends JpaRepository<DsarAttachmentEntity, UUID> {

    Optional<DsarAttachmentEntity> findByTenantIdAndDsarIdAndIdempotencyKey(
        UUID tenantId, UUID dsarId, String idempotencyKey);

    Optional<DsarAttachmentEntity> findByTenantIdAndDsarIdAndVersion(
        UUID tenantId, UUID dsarId, Integer version);

    @Query("SELECT COALESCE(MAX(a.version), 0) FROM DsarAttachmentEntity a WHERE a.tenantId = :tenantId AND a.dsarId = :dsarId")
    Integer findMaxVersionByTenantIdAndDsarId(@Param("tenantId") UUID tenantId, @Param("dsarId") UUID dsarId);

    List<DsarAttachmentEntity> findByTenantIdAndDsarIdOrderByCreatedAtAsc(UUID tenantId, UUID dsarId);
}