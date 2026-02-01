package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.RightsGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RightsGrantRepository extends JpaRepository<RightsGrant, UUID> {

    List<RightsGrant> findByNomineeId(UUID nomineeId);

    List<RightsGrant> findByDataPrincipalId(UUID dataPrincipalId);

    List<RightsGrant> findByTenantIdAndDataPrincipalId(UUID tenantId, UUID dataPrincipalId);

    @Query("SELECT r FROM RightsGrant r WHERE r.nomineeId = :nomineeId AND r.status = 'ACTIVE'")
    List<RightsGrant> findActiveGrantsByNomineeId(UUID nomineeId);
}
