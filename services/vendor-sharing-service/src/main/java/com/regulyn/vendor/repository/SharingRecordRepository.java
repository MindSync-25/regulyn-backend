package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.SharingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharingRecordRepository extends JpaRepository<SharingRecord, UUID> {

    Optional<SharingRecord> findByTenantIdAndSharingId(UUID tenantId, UUID sharingId);

    Page<SharingRecord> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<SharingRecord> findByTenantIdAndVendorIdOrderByCreatedAtDesc(
        UUID tenantId, UUID vendorId, Pageable pageable);

    Page<SharingRecord> findByTenantIdAndEnabledOrderByCreatedAtDesc(
        UUID tenantId, Boolean enabled, Pageable pageable);

    Page<SharingRecord> findByTenantIdAndActivityIdOrderByCreatedAtDesc(
        UUID tenantId, UUID activityId, Pageable pageable);

    Page<SharingRecord> findByTenantIdAndSystemIdOrderByCreatedAtDesc(
        UUID tenantId, UUID systemId, Pageable pageable);

    Page<SharingRecord> findByTenantIdAndTransferCrossBorderOrderByCreatedAtDesc(
        UUID tenantId, Boolean transferCrossBorder, Pageable pageable);

    @Query(value = "SELECT * FROM vendor.sharing_records WHERE tenant_id = :tenantId " +
           "AND :dataCategory = ANY(data_categories) " +
           "ORDER BY created_at DESC",
           nativeQuery = true)
    Page<SharingRecord> findByDataCategory(
        @Param("tenantId") UUID tenantId, 
        @Param("dataCategory") String dataCategory, 
        Pageable pageable);

    List<SharingRecord> findByTenantIdAndEnabledOrderByCreatedAtDesc(UUID tenantId, Boolean enabled);

    @Query("SELECT sr FROM SharingRecord sr WHERE sr.tenantId = :tenantId " +
           "AND sr.createdAt BETWEEN :from AND :to " +
           "ORDER BY sr.createdAt DESC")
    List<SharingRecord> findByTenantIdAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("from") java.time.Instant from,
        @Param("to") java.time.Instant to);
}
