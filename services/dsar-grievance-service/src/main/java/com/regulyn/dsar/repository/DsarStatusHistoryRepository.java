package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DsarStatusHistoryRepository extends JpaRepository<DsarStatusHistory, UUID> {
    
    List<DsarStatusHistory> findByDsarIdOrderByChangedAtDesc(UUID dsarId);
    
    List<DsarStatusHistory> findByTenantIdAndDsarIdOrderByChangedAtDesc(UUID tenantId, UUID dsarId);
}
