package com.regulyn.consent.repository;

import com.regulyn.consent.entity.ConsentReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentReceiptRepository extends JpaRepository<ConsentReceiptEntity, UUID> {
    
    Optional<ConsentReceiptEntity> findByTenantIdAndDataPrincipalIdAndPurposeAndIdempotencyKey(
        UUID tenantId, UUID dataPrincipalId, String purpose, String idempotencyKey);
    
    List<ConsentReceiptEntity> findByTenantIdAndDataPrincipalIdOrderByGrantedAtDesc(UUID tenantId, UUID dataPrincipalId);
    
    List<ConsentReceiptEntity> findByTenantIdAndDataPrincipalIdAndPurposeOrderByGrantedAtDesc(
        UUID tenantId, UUID dataPrincipalId, String purpose);
}
