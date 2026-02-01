package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.VendorExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorExportRepository extends JpaRepository<VendorExport, UUID> {

    Optional<VendorExport> findByTenantIdAndExportId(UUID tenantId, UUID exportId);

    List<VendorExport> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
