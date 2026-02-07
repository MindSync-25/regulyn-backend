package com.regulyn.notification.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class ConsentClient {

    private final RestTemplate restTemplate;
    private final String consentBaseUrl;

    public ConsentClient(
        @Qualifier("consentRestTemplate") RestTemplate restTemplate,
        @Value("${consent.baseUrl:http://localhost:8084}") String consentBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.consentBaseUrl = consentBaseUrl;
    }

    public boolean isConsentAllowed(String tenantId, String recipientId, String channel, String category) {
        String url = consentBaseUrl + "/consent/check";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
            "tenantId", tenantId,
            "recipientId", recipientId,
            "channel", channel,
            "category", category
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Map.class);
        Map body = response.getBody();
        if (body == null || !body.containsKey("allowed")) {
            throw new IllegalStateException("Consent service response missing 'allowed'");
        }
        Object allowed = body.get("allowed");
        if (allowed instanceof Boolean) {
            return (Boolean) allowed;
        }
        if (allowed instanceof String) {
            return Boolean.parseBoolean((String) allowed);
        }
        throw new IllegalStateException("Consent service response 'allowed' not boolean");
    }
}
