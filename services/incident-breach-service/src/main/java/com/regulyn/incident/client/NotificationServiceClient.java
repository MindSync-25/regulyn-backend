package com.regulyn.incident.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;
    private final boolean stubMode;
    
    public NotificationServiceClient(
            RestTemplate restTemplate,
            @Value("${notification.service.url:http://localhost:8087}") String notificationServiceUrl,
            @Value("${notification.service.stub:true}") boolean stubMode) {
        this.restTemplate = restTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
        this.stubMode = stubMode;
    }
    
    public Map<String, Object> sendNotification(UUID tenantId, String channel, String text, UUID notificationId) {
        if (stubMode) {
            logger.info("STUB: Sending notification {} via {} for tenant {}", notificationId, channel, tenantId);
            return Map.of(
                "messageId", UUID.randomUUID().toString(),
                "status", "SENT",
                "sentAt", Instant.now().toString(),
                "channel", channel,
                "stubMode", true
            );
        }
        
        try {
            String url = notificationServiceUrl + "/send";
            
            Map<String, Object> request = Map.of(
                "channel", channel,
                "message", text,
                "notificationId", notificationId.toString()
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Sent notification {}: {}", notificationId, response.getBody());
                return response.getBody();
            }
            
            throw new RuntimeException("Failed to send notification: " + response.getStatusCode());
            
        } catch (RestClientException e) {
            logger.error("Notification service error", e);
            throw new RuntimeException("Notification service error: " + e.getMessage());
        }
    }

    public NotificationSendResult sendEmail(UUID tenantId,
                                            String to,
                                            String subject,
                                            String body,
                                            UUID draftId,
                                            UUID dispatchLogId) {
        if (stubMode) {
            logger.info("STUB: Sending email to {} for tenant {}", to, tenantId);
            return new NotificationSendResult(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "SENT"
            );
        }

        try {
            String url = notificationServiceUrl + "/send";

            Map<String, Object> request = Map.of(
                "channel", "EMAIL",
                "to", to,
                "subject", subject,
                "body", body,
                "draftId", draftId != null ? draftId.toString() : null,
                "dispatchLogId", dispatchLogId != null ? dispatchLogId.toString() : null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                String requestId = asString(bodyMap.get("notificationRequestId"));
                if (requestId == null) {
                    requestId = asString(bodyMap.get("requestId"));
                }
                String providerMessageId = asString(bodyMap.get("providerMessageId"));
                if (providerMessageId == null) {
                    providerMessageId = asString(bodyMap.get("messageId"));
                }
                String status = asString(bodyMap.get("status"));

                logger.info("Sent email to {}: {}", to, bodyMap);
                return new NotificationSendResult(requestId, providerMessageId, status);
            }

            throw new RuntimeException("Failed to send email: " + response.getStatusCode());

        } catch (RestClientException e) {
            logger.error("Notification service error", e);
            throw new RuntimeException("Notification service error: " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
