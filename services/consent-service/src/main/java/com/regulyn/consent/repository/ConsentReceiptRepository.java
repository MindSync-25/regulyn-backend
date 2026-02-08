package com.regulyn.consent.repository;

import com.regulyn.consent.entity.ConsentReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    Optional<ConsentReceiptEntity> findTopByTenantIdAndDataPrincipalIdAndPurposeAndStatusAndPurposeVersionIdOrderByGrantedAtDesc(
        UUID tenantId,
        UUID dataPrincipalId,
        String purpose,
        String status,
        UUID purposeVersionId);

    @Query("select r from ConsentReceiptEntity r where r.tenantId = ?1 and r.purpose = ?2 and r.status = 'GRANTED' and (r.purposeVersionId = ?3 or r.purposeVersionId is null)")
    List<ConsentReceiptEntity> findGrantedForPurposeAndPriorOrLegacy(UUID tenantId, String purpose, UUID priorPurposeVersionId);
}
