package io.regulyn.connector.repository;

import io.regulyn.connector.model.WebhookEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WebhookEvent entities.
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /**
     * Find recent webhook events by connector, ordered by received_at descending.
     * 
     * @param tenantId Tenant ID
     * @param connectorId Connector ID
     * @param pageable Pagination parameters
     * @return List of recent webhook events
     */
    List<WebhookEvent> findByTenantIdAndConnectorIdOrderByReceivedAtDesc(UUID tenantId, UUID connectorId, Pageable pageable);

    /**
     * Find webhook event by correlation ID.
     * 
     * @param tenantId Tenant ID
     * @param correlationId Correlation ID
     * @return Optional containing the webhook event if found
     */
    Optional<WebhookEvent> findByTenantIdAndCorrelationId(UUID tenantId, String correlationId);

    /**
     * Count duplicate webhook events by payload hash received after a certain time.
     * Used for idempotency detection within a time window.
     * 
     * @param tenantId Tenant ID
     * @param connectorId Connector ID
     * @param payloadHash SHA-256 hash of the payload
     * @param after Only count events received after this timestamp
     * @return Count of matching webhook events
     */
    long countByTenantIdAndConnectorIdAndPayloadHashAndReceivedAtAfter(
            UUID tenantId,
            UUID connectorId,
            String payloadHash,
            Instant after
    );
}
