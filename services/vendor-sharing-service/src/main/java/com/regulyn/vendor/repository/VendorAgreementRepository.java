package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.VendorAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorAgreementRepository extends JpaRepository<VendorAgreement, UUID> {

    List<VendorAgreement> findByTenantIdAndVendorIdOrderByCreatedAtDesc(UUID tenantId, UUID vendorId);

    List<VendorAgreement> findByTenantIdAndVendorIdAndStatusOrderByCreatedAtDesc(
        UUID tenantId, UUID vendorId, VendorAgreement.AgreementStatus status);
}
