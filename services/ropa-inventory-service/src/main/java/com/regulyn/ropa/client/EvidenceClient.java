package com.regulyn.ropa.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private final RestTemplate restTemplate;
    private final String evidenceBaseUrl;

    public EvidenceClient(RestTemplate restTemplate,
                          @Value("${evidence.base-url:http://localhost:8083}") String evidenceBaseUrl) {
        this.restTemplate = restTemplate;
        this.evidenceBaseUrl = evidenceBaseUrl;
    }

    public UUID createEvidence(Map<String, Object> payload) {
        String url = evidenceBaseUrl + "/evidence";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to create evidence: " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("evidenceId")) {
            throw new RuntimeException("Invalid response from evidence service");
        }

        return UUID.fromString(body.get("evidenceId").toString());
    }

    public UUID createBundle(Map<String, Object> payload) {
        String url = evidenceBaseUrl + "/bundles";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to create bundle: " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("bundleId")) {
            throw new RuntimeException("Invalid response from evidence service");
        }

        return UUID.fromString(body.get("bundleId").toString());
    }

    public UUID createExport(UUID bundleId) {
        String url = evidenceBaseUrl + "/bundles/" + bundleId + "/export";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to create export: " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("exportId")) {
            throw new RuntimeException("Invalid response from evidence service");
        }

        return UUID.fromString(body.get("exportId").toString());
    }

    public byte[] downloadExport(UUID exportId) {
        String url = evidenceBaseUrl + "/exports/" + exportId + "/download";

        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to download export: " + response.getStatusCode());
        }

        return response.getBody();
    }
}
