package com.regulyn.dsar.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;

    public NotificationServiceClient(
        RestTemplate restTemplate,
        @Value("${notification.service.url:http://localhost:8087}") String notificationServiceUrl) {
        this.restTemplate = restTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    public NotificationSendResult sendEscalationEmail(
        UUID tenantId,
        String requestRef,
        String templateKey,
        String language,
        List<String> recipients,
        Map<String, String> variables
    ) {
        try {
            String url = notificationServiceUrl + "/api/notifications/send";

            Map<String, Object> request = new HashMap<>();
            request.put("requestRef", requestRef);
            request.put("templateKey", templateKey);
            request.put("language", language);
            request.put("channel", "EMAIL");
            request.put("audience", Map.of(
                "type", "USER_IDS",
                "userIds", recipients
            ));
            request.put("variables", variables);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String requestId = asString(body.get("notificationRequestId"));
                if (requestId == null) {
                    requestId = asString(body.get("requestId"));
                }
                String providerMessageId = asString(body.get("providerMessageId"));
                if (providerMessageId == null) {
                    providerMessageId = asString(body.get("messageId"));
                }
                return new NotificationSendResult(requestId, providerMessageId);
            }

            throw new RuntimeException("Notification service responded with status " + response.getStatusCode());
        } catch (RestClientException ex) {
            log.error("Notification service error", ex);
            throw new RuntimeException("Notification service error: " + ex.getMessage(), ex);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
