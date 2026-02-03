package com.regulyn.notification.client;

import com.regulyn.notification.entity.MessageCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Client for consent-service API to validate consent status.
 * 
 * FAIL-CLOSED by default: If consent check fails, notification is BLOCKED
 * LEGAL_MANDATORY bypass: Only legal mandatory messages may bypass on failure
 */
@Component
@ConditionalOnProperty(prefix = "notification.consent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConsentServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsentServiceClient.class);
    
    private final WebClient webClient;
    private final boolean enabled;
    
    public ConsentServiceClient(
        WebClient.Builder webClientBuilder,
        @Value("${notification.consent.service-url:http://consent-service:8080}") String serviceUrl,
        @Value("${notification.consent.enabled:true}") boolean enabled
    ) {
        this.webClient = webClientBuilder
            .baseUrl(serviceUrl)
            .build();
        this.enabled = enabled;
    }
    
    /**
     * Check if user has OPT_IN consent for the given channel and category.
     * 
     * FAIL-CLOSED by default: Returns ConsentCheckResult with checkFailed=true on errors
     * Caller must inspect result and decide whether to proceed based on message category
     * 
     * @param tenantId Tenant ID
     * @param dataPrincipalId Data principal ID (user ID)
     * @param channel Communication channel (EMAIL, SMS, WHATSAPP)
     * @param category Consent category (LEGAL, MARKETING, SECURITY, OPERATIONS)
     * @return ConsentCheckResult with hasConsent, checkFailed, and reason
     */
    public ConsentCheckResult checkConsent(String tenantId, String dataPrincipalId, String channel, String category) {
        if (!enabled) {
            logger.debug("Consent check disabled, allowing send");
            return new ConsentCheckResult(true, false, "CONSENT_DISABLED");
        }
        
        try {
            ConsentStatus status = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/consent/check")
                    .queryParam("tenantId", tenantId)
                    .queryParam("dataPrincipalId", dataPrincipalId)
                    .queryParam("channel", channel)
                    .queryParam("category", category)
                    .build())
                .retrieve()
                .bodyToMono(ConsentStatus.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    logger.error("Consent service error for dp={}, channel={}, category={}: {}", 
                        dataPrincipalId, channel, category, e.getMessage());
                    // Return error marker
                    return Mono.just(new ConsentStatus(false, "ERROR", "Service unavailable: " + e.getMessage()));
                })
                .block();
            
            if (status == null) {
                logger.error("Consent check returned null for dp={}, channel={}, category={}", 
                    dataPrincipalId, channel, category);
                return new ConsentCheckResult(false, true, "CONSENT_CHECK_NULL_RESPONSE");
            }
            
            if ("ERROR".equals(status.status())) {
                logger.error("Consent check FAILED for dp={}, channel={}, category={}: {}", 
                    dataPrincipalId, channel, category, status.reason());
                return new ConsentCheckResult(false, true, "CONSENT_CHECK_UNAVAILABLE");
            }
            
            boolean hasConsent = status.hasConsent();
            
            if (!hasConsent) {
                logger.info("Consent check NO CONSENT: tenant={}, dp={}, channel={}, category={}, status={}", 
                    tenantId, dataPrincipalId, channel, category, status.status());
                return new ConsentCheckResult(false, false, "NO_CONSENT");
            }
            
            return new ConsentCheckResult(true, false, "OPT_IN");
            
        } catch (Exception e) {
            logger.error("Unexpected error checking consent for dp={}, channel={}, category={}: {}", 
                dataPrincipalId, channel, category, e.getMessage(), e);
            // Fail-closed: block send on error
            return new ConsentCheckResult(false, true, "CONSENT_CHECK_EXCEPTION");
        }
    }
    
    /**
     * Backward-compatible method that returns boolean
     * DEPRECATED: Use checkConsent() for fail-closed behavior
     */
    @Deprecated
    public boolean hasOptInConsent(String tenantId, String dataPrincipalId, String channel, String category) {
        ConsentCheckResult result = checkConsent(tenantId, dataPrincipalId, channel, category);
        return result.hasConsent() && !result.checkFailed();
    }
    
    /**
     * Batch check consent for multiple data principals
     * 
     * @param tenantId Tenant ID
     * @param dataPrincipalIds List of data principal IDs
     * @param channel Communication channel
     * @param category Consent category
     * @return List of data principal IDs that have OPT_IN consent
     */
    public List<String> filterByOptInConsent(String tenantId, List<String> dataPrincipalIds, String channel, String category) {
        if (!enabled) {
            logger.debug("Consent check disabled, returning all data principals");
            return dataPrincipalIds;
        }
        
        return dataPrincipalIds.stream()
            .filter(dpId -> hasOptInConsent(tenantId, dpId, channel, category))
            .toList();
    }
    
    public record ConsentStatus(
        boolean hasConsent,
        String status,  // OPT_IN, OPT_OUT, NOT_SET, ERROR
        String reason
    ) {}
    
    /**
     * Result of consent check with fail-closed semantics
     */
    public record ConsentCheckResult(
        boolean hasConsent,  // true if user has OPT_IN consent
        boolean checkFailed, // true if consent service call failed (error/timeout)
        String reason        // Detailed reason: OPT_IN, NO_CONSENT, CONSENT_CHECK_UNAVAILABLE, etc.
    ) {}
}
