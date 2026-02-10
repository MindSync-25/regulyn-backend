package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.CrossBorderTransferPurposeVersionEntity;
import com.regulyn.ropa.model.CrossBorderTransferPurposeVersionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CrossBorderTransferPurposeVersionRepository extends JpaRepository<CrossBorderTransferPurposeVersionEntity, CrossBorderTransferPurposeVersionId> {

	List<CrossBorderTransferPurposeVersionEntity> findByTenantIdAndIdTransferId(UUID tenantId, UUID transferId);

	List<CrossBorderTransferPurposeVersionEntity> findByTenantIdAndIdTransferIdIn(UUID tenantId, Collection<UUID> transferIds);

	void deleteByTenantIdAndIdTransferId(UUID tenantId, UUID transferId);
}
