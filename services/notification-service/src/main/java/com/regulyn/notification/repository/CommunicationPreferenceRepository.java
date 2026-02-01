package com.regulyn.notification.repository;

import com.regulyn.notification.entity.CommunicationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunicationPreferenceRepository extends JpaRepository<CommunicationPreference, UUID> {
    
    @Query("SELECT p FROM CommunicationPreference p WHERE p.tenantId = :tenantId AND p.dataPrincipalId = :dataPrincipalId")
    List<CommunicationPreference> findByTenantIdAndDataPrincipalId(
        @Param("tenantId") String tenantId, 
        @Param("dataPrincipalId") String dataPrincipalId
    );
    
    @Query("SELECT p FROM CommunicationPreference p WHERE p.tenantId = :tenantId AND p.dataPrincipalId = :dataPrincipalId AND p.channel = :channel AND p.category = :category")
    Optional<CommunicationPreference> findByTenantIdAndDataPrincipalIdAndChannelAndCategory(
        @Param("tenantId") String tenantId, 
        @Param("dataPrincipalId") String dataPrincipalId, 
        @Param("channel") String channel, 
        @Param("category") String category
    );
    
    @Query("SELECT p FROM CommunicationPreference p WHERE p.tenantId = :tenantId AND p.dataPrincipalId = :dataPrincipalId AND p.channel = :channel AND p.category = :category AND p.optedOut = true")
    Optional<CommunicationPreference> findOptedOut(
        @Param("tenantId") String tenantId, 
        @Param("dataPrincipalId") String dataPrincipalId, 
        @Param("channel") String channel, 
        @Param("category") String category
    );
}
