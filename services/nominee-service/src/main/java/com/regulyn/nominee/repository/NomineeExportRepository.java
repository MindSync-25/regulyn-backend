package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.NomineeExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NomineeExportRepository extends JpaRepository<NomineeExport, UUID> {

    List<NomineeExport> findByNomineeIdOrderByRequestedAtDesc(UUID nomineeId);

    List<NomineeExport> findByTenantIdAndStatus(UUID tenantId, String status);
}
