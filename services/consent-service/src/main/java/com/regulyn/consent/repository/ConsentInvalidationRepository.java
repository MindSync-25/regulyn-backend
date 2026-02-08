package com.regulyn.consent.repository;

import com.regulyn.consent.entity.ConsentInvalidation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentInvalidationRepository extends JpaRepository<ConsentInvalidation, UUID> {
    boolean existsByTenantIdAndConsentReceiptId(UUID tenantId, UUID consentReceiptId);
}
