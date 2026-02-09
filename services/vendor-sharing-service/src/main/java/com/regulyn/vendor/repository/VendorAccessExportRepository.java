package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.VendorAccessExportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorAccessExportRepository extends JpaRepository<VendorAccessExportEntity, UUID> {

    Optional<VendorAccessExportEntity> findByTenantIdAndExportId(UUID tenantId, UUID exportId);

    Optional<VendorAccessExportEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<VendorAccessExportEntity> findByStatusOrderByRequestedAtAsc(String status);

    List<VendorAccessExportEntity> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);
}
