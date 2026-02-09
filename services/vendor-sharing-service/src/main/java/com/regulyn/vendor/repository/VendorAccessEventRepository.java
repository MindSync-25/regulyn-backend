package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.VendorAccessEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorAccessEventRepository extends JpaRepository<VendorAccessEventEntity, UUID>, JpaSpecificationExecutor<VendorAccessEventEntity> {

    Optional<VendorAccessEventEntity> findByTenantIdAndVendorIdAndCorrelationIdAndAccessedAtAndAccessType(
        UUID tenantId,
        UUID vendorId,
        String correlationId,
        OffsetDateTime accessedAt,
        String accessType
    );
}
