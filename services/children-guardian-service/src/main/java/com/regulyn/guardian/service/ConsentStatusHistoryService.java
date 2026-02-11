package com.regulyn.guardian.service;

import com.regulyn.guardian.entity.ConsentStatusHistory;
import com.regulyn.guardian.repository.ConsentStatusHistoryRepository;
import com.regulyn.auth.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConsentStatusHistoryService {
    
    private final ConsentStatusHistoryRepository historyRepository;
    
    public ConsentStatusHistoryService(ConsentStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }
    
    @Transactional
    public void recordStatusChange(
        UUID consentId,
        String fromStatus,
        String toStatus,
        UUID changedBy,
        String reason
    ) {
        ConsentStatusHistory history = new ConsentStatusHistory();
        history.setTenantId(TenantContextHolder.getContext().getTenantId());
        history.setConsentId(consentId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        history.setReason(reason);
        
        historyRepository.save(history);
    }
    
    public List<ConsentStatusHistory> getHistory(UUID consentId) {
        return historyRepository.findByConsentIdOrderByChangedAtAsc(consentId);
    }
    
    public List<ConsentStatusHistory> getHistoryDesc(UUID consentId) {
        return historyRepository.findByConsentIdOrderByChangedAtDesc(consentId);
    }
}
