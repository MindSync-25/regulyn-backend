package io.regulyn.identity.repository;

import io.regulyn.identity.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    
    Optional<ApiKey> findByApiKeyHashAndEnabled(String apiKeyHash, Boolean enabled);

    Optional<ApiKey> findByApiKeyHash(String apiKeyHash);
    
    List<ApiKey> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApiKey a where a.apiKeyId = :apiKeyId")
    Optional<ApiKey> findByIdForUpdate(@Param("apiKeyId") UUID apiKeyId);
}
