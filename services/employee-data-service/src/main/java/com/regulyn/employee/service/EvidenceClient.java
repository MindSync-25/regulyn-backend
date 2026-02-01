package com.regulyn.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private static final Logger log = LoggerFactory.getLogger(EvidenceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EvidenceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8083}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Create evidence in the evidence-reporting-service
     * @param payload Evidence data
     * @return Evidence ID
     */
    public UUID createEvidence(Map<String, Object> payload) {
        try {
            String url = baseUrl + "/evidence";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    entity, 
                    Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                Object evidenceId = response.getBody().get("evidenceId");
                if (evidenceId != null) {
                    return UUID.fromString(evidenceId.toString());
                }
            }
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to parse evidence ID from response");

        } catch (RestClientException e) {
            log.error("Error creating evidence in evidence service", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                    "Evidence service unavailable", e);
        }
    }

    /**
     * Create evidence bundle
     * @param bundleData Bundle metadata
     * @return Bundle ID
     */
    public UUID createBundle(Map<String, Object> bundleData) {
        try {
            String url = baseUrl + "/bundles";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(bundleData, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    entity, 
                    Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                Object bundleId = response.getBody().get("bundleId");
                if (bundleId != null) {
                    return UUID.fromString(bundleId.toString());
                }
            }
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to parse bundle ID from response");

        } catch (RestClientException e) {
            log.error("Error creating bundle in evidence service", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                    "Evidence service unavailable", e);
        }
    }

    /**
     * Create export for a bundle
     * @param bundleId Bundle ID
     * @return Export ID
     */
    public UUID createExport(UUID bundleId) {
        try {
            String url = baseUrl + "/bundles/" + bundleId + "/export";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    entity, 
                    Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                Object exportId = response.getBody().get("exportId");
                if (exportId != null) {
                    return UUID.fromString(exportId.toString());
                }
            }
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to parse export ID from response");

        } catch (RestClientException e) {
            log.error("Error creating export in evidence service for bundle {}", bundleId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                    "Evidence service unavailable", e);
        }
    }

    /**
     * Download export file
     * @param exportId Export ID
     * @return File bytes
     */
    public byte[] downloadExport(UUID exportId) {
        try {
            String url = baseUrl + "/exports/" + exportId + "/download";
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, 
                    HttpMethod.GET, 
                    null, 
                    byte[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to download export");

        } catch (RestClientException e) {
            log.error("Error downloading export {} from evidence service", exportId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                    "Evidence service unavailable", e);
        }
    }
}
