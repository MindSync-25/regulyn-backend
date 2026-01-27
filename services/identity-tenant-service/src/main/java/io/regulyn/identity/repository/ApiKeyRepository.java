package io.regulyn.identity.repository;

import io.regulyn.identity.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    
    Optional<ApiKey> findByApiKeyHashAndEnabled(String apiKeyHash, Boolean enabled);
    
    List<ApiKey> findByTenantId(UUID tenantId);
}
