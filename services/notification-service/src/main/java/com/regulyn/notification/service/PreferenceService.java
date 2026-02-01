package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.notification.dto.GetPreferencesResponse;
import com.regulyn.notification.dto.OptOutRequest;
import com.regulyn.notification.dto.OptOutResponse;
import com.regulyn.notification.entity.CommunicationPreference;
import com.regulyn.notification.repository.CommunicationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PreferenceService {
    
    private final CommunicationPreferenceRepository preferenceRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public PreferenceService(
        CommunicationPreferenceRepository preferenceRepository,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter
    ) {
        this.preferenceRepository = preferenceRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Transactional
    public OptOutResponse updatePreference(OptOutRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Find existing preference or create new
        CommunicationPreference preference = preferenceRepository
            .findByTenantIdAndDataPrincipalIdAndChannelAndCategory(
                tenantId,
                request.dataPrincipalId(),
                request.channel(),
                request.category()
            )
            .orElseGet(() -> {
                CommunicationPreference newPref = new CommunicationPreference();
                newPref.setTenantId(tenantId);
                newPref.setDataPrincipalId(request.dataPrincipalId());
                newPref.setChannel(request.channel());
                newPref.setCategory(request.category());
                return newPref;
            });
        
        preference.setOptedOut(request.optedOut());
        preference.setUpdatedBy(userId);
        
        preference = preferenceRepository.save(preference);
        
        // Audit
        auditWriter.auditAction(
            request.optedOut() ? "PREFERENCE_OPTED_OUT" : "PREFERENCE_OPTED_IN",
            "CommunicationPreference",
            preference.getPreferenceId().toString(),
            null
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setActorId(UUID.fromString(userId));
        event.setEventType("notification.preference_updated");
        event.setSourceService("notification-service");
        event.setEntityType("CommunicationPreference");
        event.setEntityId(preference.getPreferenceId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
        return new OptOutResponse(preference.getPreferenceId());
    }
    
    @Transactional(readOnly = true)
    public GetPreferencesResponse getPreferences(String dataPrincipalId) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        List<CommunicationPreference> preferences = preferenceRepository
            .findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId);
        
        List<GetPreferencesResponse.Preference> prefs = preferences.stream()
            .map(p -> new GetPreferencesResponse.Preference(
                p.getChannel(),
                p.getCategory(),
                p.getOptedOut()
            ))
            .toList();
        
        return new GetPreferencesResponse(dataPrincipalId, prefs);
    }
    
    @Transactional(readOnly = true)
    public boolean isOptedOut(String dataPrincipalId, String channel, String category) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        return preferenceRepository
            .findOptedOut(tenantId, dataPrincipalId, channel, category)
            .isPresent();
    }
}
