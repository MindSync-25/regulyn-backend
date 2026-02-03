package com.regulyn.notification.repository;

import com.regulyn.notification.entity.DeliveryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, UUID> {
    
    List<DeliveryReceipt> findByTenantIdAndDispatchId(String tenantId, UUID dispatchId);
    
    Optional<DeliveryReceipt> findByProviderMessageId(String providerMessageId);
    
    List<DeliveryReceipt> findByTenantIdAndDeliveryStatus(String tenantId, DeliveryReceipt.DeliveryStatus status);
}
