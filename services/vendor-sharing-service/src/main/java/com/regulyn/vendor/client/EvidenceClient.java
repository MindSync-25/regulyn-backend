package com.regulyn.vendor.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private final RestTemplate restTemplate;
    private final String evidenceServiceUrl;

    public EvidenceClient(
        RestTemplate restTemplate,
        @Value("${evidence.service.url:http://localhost:8083}") String evidenceServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.evidenceServiceUrl = evidenceServiceUrl;
    }

    /**
     * Create an evidence record
     * POST /evidence
     */
    public UUID createEvidence(Map<String, Object> evidenceData) throws RestClientException {
        String url = evidenceServiceUrl + "/evidence";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(evidenceData, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("evidenceId")) {
            return UUID.fromString(body.get("evidenceId").toString());
        }
        throw new RuntimeException("Failed to create evidence: no evidenceId in response");
    }

    /**
     * Create an evidence bundle
     * POST /bundles
     */
    public UUID createBundle(Map<String, Object> bundleData) throws RestClientException {
        String url = evidenceServiceUrl + "/bundles";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(bundleData, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("bundleId")) {
            return UUID.fromString(body.get("bundleId").toString());
        }
        throw new RuntimeException("Failed to create bundle: no bundleId in response");
    }

    /**
     * Create an export from a bundle
     * POST /bundles/{bundleId}/export
     */
    public UUID createExport(UUID bundleId) throws RestClientException {
        String url = evidenceServiceUrl + "/bundles/" + bundleId + "/export";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("evidenceExportId")) {
            return UUID.fromString(body.get("evidenceExportId").toString());
        }
        throw new RuntimeException("Failed to create export: no evidenceExportId in response");
    }

    /**
     * Download an export
     * GET /exports/{exportId}/download
     */
    public byte[] downloadExport(UUID exportId) throws RestClientException {
        String url = evidenceServiceUrl + "/exports/" + exportId + "/download";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, request, byte[].class);
        return response.getBody();
    }
}
