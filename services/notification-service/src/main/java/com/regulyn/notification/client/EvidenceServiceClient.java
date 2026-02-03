package com.regulyn.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Client for evidence-reporting-service API to create proof artifacts
 */
@Component
@ConditionalOnProperty(prefix = "notification.evidence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EvidenceServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EvidenceServiceClient.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    
    public EvidenceServiceClient(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        @Value("${notification.evidence.service-url:http://evidence-reporting-service:8080}") String serviceUrl,
        @Value("${notification.evidence.enabled:true}") boolean enabled
    ) {
        this.webClient = webClientBuilder
            .baseUrl(serviceUrl)
            .build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }
    
    /**
     * Create evidence artifact for notification send
     * 
     * @param tenantId Tenant ID
     * @param artifactType Artifact type (NOTIFICATION_SENT, NOTIFICATION_DELIVERED, etc.)
     * @param artifactData Artifact data (message details, receipt, etc.)
     * @return Artifact ID if successful, null if failed or disabled
     */
    public String createArtifact(String tenantId, String artifactType, Map<String, Object> artifactData) {
        if (!enabled) {
            logger.debug("Evidence artifact creation disabled");
            return null;
        }
        
        try {
            EvidenceArtifactRequest request = new EvidenceArtifactRequest(
                tenantId,
                artifactType,
                "NOTIFICATION",
                objectMapper.writeValueAsString(artifactData)
            );
            
            EvidenceArtifactResponse response = webClient.post()
                .uri("/api/v1/evidence/artifacts")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EvidenceArtifactResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    logger.error("Evidence service error creating artifact type={}: {}", 
                        artifactType, e.getMessage());
                    return Mono.empty();
                })
                .block();
            
            if (response != null && response.artifactId() != null) {
                logger.info("Evidence artifact created: artifactId={}, type={}", 
                    response.artifactId(), artifactType);
                return response.artifactId();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Unexpected error creating evidence artifact type={}: {}", 
                artifactType, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create artifact for sent notification
     */
    public String createNotificationSentArtifact(
        String tenantId,
        UUID messageId,
        String recipient,
        String channel,
        String subject,
        String providerMessageId
    ) {
        return createArtifact(tenantId, "NOTIFICATION_SENT", Map.of(
            "messageId", messageId.toString(),
            "recipient", recipient,
            "channel", channel,
            "subject", subject != null ? subject : "",
            "providerMessageId", providerMessageId != null ? providerMessageId : "",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Create artifact for delivered notification
     */
    public String createNotificationDeliveredArtifact(
        String tenantId,
        String messageId,
        String recipientAddress,
        Map<String, Object> metadata
    ) {
        Map<String, Object> artifactData = new java.util.HashMap<>(metadata);
        artifactData.put("messageId", messageId);
        artifactData.put("recipientAddress", recipientAddress);
        artifactData.put("timestamp", System.currentTimeMillis());
        
        return createArtifact(tenantId, "NOTIFICATION_DELIVERED", artifactData);
    }
    
    /**
     * Create artifact for failed notification (bounced, complained, failed delivery)
     */
    public String createNotificationFailedArtifact(
        String tenantId,
        String messageId,
        String recipientAddress,
        Map<String, Object> metadata
    ) {
        Map<String, Object> artifactData = new java.util.HashMap<>(metadata);
        artifactData.put("messageId", messageId);
        artifactData.put("recipientAddress", recipientAddress);
        artifactData.put("timestamp", System.currentTimeMillis());
        
        return createArtifact(tenantId, "NOTIFICATION_FAILED", artifactData);
    }
    
    public record EvidenceArtifactRequest(
        String tenantId,
        String artifactType,
        String sourceSystem,
        String artifactData
    ) {}
    
    public record EvidenceArtifactResponse(
        String artifactId,
        String status
    ) {}
}
