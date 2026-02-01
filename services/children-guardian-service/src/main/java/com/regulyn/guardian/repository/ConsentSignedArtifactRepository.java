package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.ConsentSignedArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentSignedArtifactRepository extends JpaRepository<ConsentSignedArtifact, UUID> {
    
    Optional<ConsentSignedArtifact> findByConsentId(UUID consentId);
    
    List<ConsentSignedArtifact> findAllByConsentId(UUID consentId);
}
