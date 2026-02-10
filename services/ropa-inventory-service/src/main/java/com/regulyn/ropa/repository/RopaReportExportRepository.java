package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaReportExportEntity;
import com.regulyn.ropa.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RopaReportExportRepository extends JpaRepository<RopaReportExportEntity, UUID> {

	Optional<RopaReportExportEntity> findByTenantIdAndReportTypeAndIdempotencyKey(UUID tenantId, ReportType reportType, String idempotencyKey);
}
