package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaExportRepository extends JpaRepository<RopaExport, UUID> {

    List<RopaExport> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
