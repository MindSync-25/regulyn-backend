package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    List<ClaimDocument> findByClaimId(UUID claimId);

    List<ClaimDocument> findByClaimIdAndDocType(UUID claimId, String docType);
}
