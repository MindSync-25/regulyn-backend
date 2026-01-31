package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.ChildProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChildProfileRepository extends JpaRepository<ChildProfile, UUID> {

    Optional<ChildProfile> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ChildProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Page<ChildProfile> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM ChildProfile c WHERE c.tenantId = :tenantId AND c.isChild = true")
    Page<ChildProfile> findChildrenByTenantId(UUID tenantId, Pageable pageable);
}
