package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.SharingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SharingStatusHistoryRepository extends JpaRepository<SharingStatusHistory, UUID> {

    List<SharingStatusHistory> findByTenantIdAndSharingIdOrderByChangedAtDesc(UUID tenantId, UUID sharingId);
}
