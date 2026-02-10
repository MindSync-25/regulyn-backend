package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.CrossBorderTransferDataCategoryEntity;
import com.regulyn.ropa.model.CrossBorderTransferDataCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CrossBorderTransferDataCategoryRepository extends JpaRepository<CrossBorderTransferDataCategoryEntity, CrossBorderTransferDataCategoryId> {

	List<CrossBorderTransferDataCategoryEntity> findByTenantIdAndIdTransferId(UUID tenantId, UUID transferId);

	List<CrossBorderTransferDataCategoryEntity> findByTenantIdAndIdTransferIdIn(UUID tenantId, Collection<UUID> transferIds);

	void deleteByTenantIdAndIdTransferId(UUID tenantId, UUID transferId);
}
