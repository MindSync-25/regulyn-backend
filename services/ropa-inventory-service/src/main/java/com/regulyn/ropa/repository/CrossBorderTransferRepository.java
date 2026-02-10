package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.CrossBorderTransferEntity;
import com.regulyn.ropa.model.TransferMechanism;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrossBorderTransferRepository extends JpaRepository<CrossBorderTransferEntity, UUID> {

    List<CrossBorderTransferEntity> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    List<CrossBorderTransferEntity> findByTenantIdAndActivityId(UUID tenantId, UUID activityId);

    List<CrossBorderTransferEntity> findByTenantIdAndSystemId(UUID tenantId, UUID systemId);

    List<CrossBorderTransferEntity> findByTenantIdAndSourceRegionAndDestinationRegion(
            UUID tenantId,
            String sourceRegion,
            String destinationRegion
    );

    Optional<CrossBorderTransferEntity> findByTenantIdAndActivityIdAndVendorIdAndSourceRegionAndDestinationRegionAndTransferMechanism(
            UUID tenantId,
            UUID activityId,
            UUID vendorId,
            String sourceRegion,
            String destinationRegion,
            TransferMechanism transferMechanism
    );

        Optional<CrossBorderTransferEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
