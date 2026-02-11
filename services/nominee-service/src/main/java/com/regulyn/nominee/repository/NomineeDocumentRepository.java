package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.NomineeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NomineeDocumentRepository extends JpaRepository<NomineeDocument, UUID> {
    List<NomineeDocument> findByTenantIdAndNomineeIdOrderByUploadedAtDesc(UUID tenantId, UUID nomineeId);
    List<NomineeDocument> findByTenantIdAndNomineeIdAndVerificationStep(UUID tenantId, UUID nomineeId, String verificationStep);
    Optional<NomineeDocument> findFirstByTenantIdAndNomineeIdAndSha256Hash(UUID tenantId, UUID nomineeId, String sha256Hash);
    Optional<NomineeDocument> findFirstByTenantIdAndNomineeIdAndIdempotencyKey(UUID tenantId, UUID nomineeId, String idempotencyKey);
    long countByTenantIdAndNomineeIdAndVerificationStep(UUID tenantId, UUID nomineeId, String verificationStep);
    List<NomineeDocument> findByTenantIdAndClaimId(UUID tenantId, UUID claimId);
    List<NomineeDocument> findByTenantIdAndNomineeId(UUID tenantId, UUID nomineeId);
}
