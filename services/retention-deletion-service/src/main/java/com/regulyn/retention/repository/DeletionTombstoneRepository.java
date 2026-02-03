package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionTombstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionTombstoneRepository extends JpaRepository<DeletionTombstone, UUID> {
    
    boolean existsByTenantIdAndSubjectRef(UUID tenantId, String subjectRef);
    
    Optional<DeletionTombstone> findByTenantIdAndSubjectRef(UUID tenantId, String subjectRef);
}
