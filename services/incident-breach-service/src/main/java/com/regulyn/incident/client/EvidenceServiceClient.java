package com.regulyn.incident.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EvidenceServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String evidenceServiceUrl;
    
    public EvidenceServiceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8086}") String evidenceServiceUrl) {
        this.restTemplate = restTemplate;
        this.evidenceServiceUrl = evidenceServiceUrl;
    }
    
    public UUID createEvidence(UUID tenantId, String description, Map<String, Object> metadata) {
        try {
            String url = evidenceServiceUrl + "/evidence";
            
            Map<String, Object> request = Map.of(
                "description", description,
                "metadata", metadata,
                "timestamp", Instant.now().toString()
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String evidenceId = (String) response.getBody().get("evidenceId");
                logger.info("Created evidence: {}", evidenceId);
                return UUID.fromString(evidenceId);
            }
            
            throw new EvidenceServiceUnavailableException("Failed to create evidence: " + response.getStatusCode());
            
        } catch (RestClientException e) {
            logger.error("Evidence service unavailable", e);
            throw new EvidenceServiceUnavailableException("Evidence service unavailable: " + e.getMessage());
        }
    }
    
    public UUID createBundle(UUID tenantId, String bundleType, String referenceType, UUID referenceId, List<UUID> evidenceIds) {
        try {
            String url = evidenceServiceUrl + "/bundles";
            
            Map<String, Object> request = Map.of(
                "bundleType", bundleType,
                "referenceType", referenceType,
                "referenceId", referenceId.toString(),
                "evidenceIds", evidenceIds.stream().map(UUID::toString).toList()
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String bundleId = (String) response.getBody().get("bundleId");
                logger.info("Created evidence bundle: {}", bundleId);
                return UUID.fromString(bundleId);
            }
            
            throw new EvidenceServiceUnavailableException("Failed to create bundle: " + response.getStatusCode());
            
        } catch (RestClientException e) {
            logger.error("Evidence service unavailable", e);
            throw new EvidenceServiceUnavailableException("Evidence service unavailable: " + e.getMessage());
        }
    }
    
    public static class EvidenceServiceUnavailableException extends RuntimeException {
        public EvidenceServiceUnavailableException(String message) {
            super(message);
        }
    }
}
