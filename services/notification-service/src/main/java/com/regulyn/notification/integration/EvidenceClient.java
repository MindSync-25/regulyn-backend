package com.regulyn.notification.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class EvidenceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EvidenceClient.class);
    
    private final RestTemplate restTemplate;
    private final String evidenceServiceUrl;
    
    public EvidenceClient(
        RestTemplate restTemplate,
        @Value("${evidence.service.url:http://localhost:8083}") String evidenceServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.evidenceServiceUrl = evidenceServiceUrl;
    }
    
    public String createNotificationSentArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationDeliveredArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationFailedArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationReceiptArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationConsentBlockedArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationConsentCheckFailedArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    public String createNotificationBypassedConsentArtifact(Map<String, Object> payload, String idempotencyKey) {
        return createEvidenceArtifact(payload, idempotencyKey);
    }

    private String createEvidenceArtifact(Map<String, Object> payload, String idempotencyKey) {
        String url = evidenceServiceUrl + "/evidence";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("evidenceId")) {
                return body.get("evidenceId").toString();
            }
            return idempotencyKey;
        } catch (HttpClientErrorException.Conflict e) {
            logger.info("Evidence already exists for idempotency key {}", idempotencyKey);
            return idempotencyKey;
        }
    }
}