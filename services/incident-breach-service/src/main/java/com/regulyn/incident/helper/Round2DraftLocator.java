package com.regulyn.incident.helper;

import com.regulyn.incident.entity.IncidentNotification;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.exception.DraftNotFoundException;
import com.regulyn.incident.repository.IncidentNotificationRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class Round2DraftLocator {

    private final IncidentNotificationRepository notificationRepository;
    private final NoticeDraftRepository draftRepository;

    public Round2DraftLocator(IncidentNotificationRepository notificationRepository,
                              NoticeDraftRepository draftRepository) {
        this.notificationRepository = notificationRepository;
        this.draftRepository = draftRepository;
    }

    public UUID findDraftIdForNotification(UUID tenantId, UUID notificationId) {
        IncidentNotification notification = notificationRepository
                .findByTenantIdAndNotificationId(tenantId, notificationId)
                .orElseThrow(() -> new DraftNotFoundException("Notification not found"));

        UUID draftId = notification.getRound2DraftId();
        if (draftId == null) {
            throw new DraftNotFoundException("Round-2 draft mapping not found");
        }

        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Round-2 draft not found"));

        return draft.getId();
    }
}
