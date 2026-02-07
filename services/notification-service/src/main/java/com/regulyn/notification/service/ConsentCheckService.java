package com.regulyn.notification.service;

import com.regulyn.notification.integration.ConsentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsentCheckService {

    private final PreferenceService preferenceService;
    private final ConsentClient consentClient;
    private final boolean consentEnabled;

    public ConsentCheckService(
        PreferenceService preferenceService,
        ConsentClient consentClient,
        @Value("${consent.enabled:true}") boolean consentEnabled
    ) {
        this.preferenceService = preferenceService;
        this.consentClient = consentClient;
        this.consentEnabled = consentEnabled;
    }

    public ConsentDecision checkConsent(
        String tenantId,
        String recipientId,
        String channel,
        String category
    ) {
        boolean optedOut = preferenceService.isOptedOut(recipientId, channel, category);
        if ("MARKETING".equals(category) && optedOut) {
            return new ConsentDecision(ConsentDecision.Outcome.BLOCK, "OPTED_OUT", false);
        }

        if (!consentEnabled) {
            return new ConsentDecision(ConsentDecision.Outcome.ALLOW, null, false);
        }

        try {
            boolean allowed = consentClient.isConsentAllowed(tenantId, recipientId, channel, category);
            if (allowed) {
                return new ConsentDecision(ConsentDecision.Outcome.ALLOW, null, false);
            }
            return new ConsentDecision(ConsentDecision.Outcome.BLOCK, "CONSENT_DENIED", false);
        } catch (Exception ex) {
            return handleConsentFailure(category);
        }
    }

    private ConsentDecision handleConsentFailure(String category) {
        if ("MARKETING".equals(category)) {
            return new ConsentDecision(ConsentDecision.Outcome.CHECK_FAILED_BLOCK, "CONSENT_CHECK_FAILED", false);
        }
        if ("LEGAL".equals(category)) {
            return new ConsentDecision(ConsentDecision.Outcome.CHECK_FAILED_ALLOW, "CONSENT_CHECK_FAILED", true);
        }
        return new ConsentDecision(ConsentDecision.Outcome.CHECK_FAILED_ALLOW, "CONSENT_CHECK_FAILED", false);
    }
}
