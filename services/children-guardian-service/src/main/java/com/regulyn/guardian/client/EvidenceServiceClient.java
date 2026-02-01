package com.regulyn.guardian.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceServiceClient {
    
    private final RestTemplate restTemplate;
    private final String evidenceBaseUrl;
    
    public EvidenceServiceClient(
        RestTemplate restTemplate,
        @Value("${evidence.service.baseUrl:http://localhost:8083}") String evidenceBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.evidenceBaseUrl = evidenceBaseUrl;
    }
    
    /**
     * Create evidence entry
     * POST /evidence
     */
    public UUID createEvidence(String evidenceType, Map<String, Object> metadata, String contentHash) {
        String url = evidenceBaseUrl + "/evidence";
        
        Map<String, Object> request = Map.of(
            "evidenceType", evidenceType,
            "metadata", metadata,
            "contentHash", contentHash
        );
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return UUID.fromString((String) response.getBody().get("evidenceId"));
            }
            
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service failed to create evidence"
            );
            
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service unavailable: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Create evidence bundle
     * POST /bundles
     */
    public UUID createBundle(String bundleType, UUID referenceId, List<UUID> evidenceIds) {
        String url = evidenceBaseUrl + "/bundles";
        
        Map<String, Object> request = Map.of(
            "bundleType", bundleType,
            "referenceId", referenceId.toString(),
            "evidenceIds", evidenceIds
        );
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return UUID.fromString((String) response.getBody().get("bundleId"));
            }
            
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service failed to create bundle"
            );
            
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service unavailable: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Create export from bundle
     * POST /bundles/{bundleId}/export
     */
    public UUID createExport(UUID bundleId) {
        String url = evidenceBaseUrl + "/bundles/" + bundleId + "/export";
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return UUID.fromString((String) response.getBody().get("exportId"));
            }
            
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service failed to create export"
            );
            
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service unavailable: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Download export
     * GET /exports/{exportId}/download
     */
    public byte[] downloadExport(UUID exportId) {
        String url = evidenceBaseUrl + "/exports/" + exportId + "/download";
        
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service failed to download export"
            );
            
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Evidence service unavailable: " + e.getMessage(),
                e
            );
        }
    }
}
