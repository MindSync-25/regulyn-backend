package com.regulyn.guardian.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class NotificationClient {

    private final RestTemplate restTemplate;
    private final String notificationBaseUrl;

    public NotificationClient(
            RestTemplate restTemplate,
            @Value("${notification.service.baseUrl:http://localhost:8086}") String notificationBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.notificationBaseUrl = notificationBaseUrl;
    }

    public void sendEmail(Map<String, Object> payload) {
        String url = notificationBaseUrl + "/notifications/email";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(response.getStatusCode(), "Notification service failed");
            }
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Notification service unavailable: " + e.getMessage(),
                    e
            );
        }
    }

    public Map<String, Object> buildEmailPayload(
            String tenantId,
            List<String> toEmails,
            String templateKey,
            Map<String, Object> variables
    ) {
        return Map.of(
                "tenantId", tenantId,
                "toEmails", toEmails,
                "templateKey", templateKey,
                "variables", variables
        );
    }
}
