package io.regulyn.identity.repository;

import io.regulyn.identity.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    Optional<Tenant> findByTenantIdAndStatus(UUID tenantId, String status);
    
    List<Tenant> findByStatus(String status);
    
    Page<Tenant> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.tenantId = :tenantId")
    Optional<Tenant> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
