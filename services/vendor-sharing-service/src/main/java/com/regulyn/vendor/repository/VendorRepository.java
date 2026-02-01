package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    List<Vendor> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Vendor> findByTenantIdAndEnabledOrderByCreatedAtDesc(UUID tenantId, Boolean enabled);

    List<Vendor> findByTenantIdAndRiskLevelOrderByCreatedAtDesc(UUID tenantId, Vendor.RiskLevel riskLevel);

    List<Vendor> findByTenantIdAndEnabledAndRiskLevelOrderByCreatedAtDesc(
        UUID tenantId, Boolean enabled, Vendor.RiskLevel riskLevel);

    @Query("SELECT v FROM Vendor v WHERE v.tenantId = :tenantId " +
           "AND LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Vendor> searchByName(@Param("tenantId") UUID tenantId, @Param("query") String query);

    @Query("SELECT v FROM Vendor v WHERE v.tenantId = :tenantId " +
           "AND v.enabled = :enabled " +
           "AND LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Vendor> searchByNameAndEnabled(
        @Param("tenantId") UUID tenantId, 
        @Param("enabled") Boolean enabled, 
        @Param("query") String query);
}
