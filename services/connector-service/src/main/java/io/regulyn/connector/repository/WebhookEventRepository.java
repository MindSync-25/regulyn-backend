package io.regulyn.connector.repository;

import io.regulyn.connector.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    
    List<WebhookEvent> findByConnectorIdAndProcessedFalseOrderByCreatedAtAsc(UUID connectorId);
    
    List<WebhookEvent> findByTenantIdAndProviderOrderByCreatedAtDesc(UUID tenantId, String provider);
    
    long countByConnectorIdAndSignatureVerifiedTrue(UUID connectorId);
}
