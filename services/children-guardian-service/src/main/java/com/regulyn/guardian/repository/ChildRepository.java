package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChildRepository extends JpaRepository<Child, UUID> {
    
    Optional<Child> findByTenantIdAndChildId(UUID tenantId, UUID childId);
    
    Optional<Child> findByTenantIdAndChildRef(UUID tenantId, String childRef);
    
    boolean existsByTenantIdAndChildRef(UUID tenantId, String childRef);
}
