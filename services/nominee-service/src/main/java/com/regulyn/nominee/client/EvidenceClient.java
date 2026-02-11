package com.regulyn.nominee.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private static final Logger log = LoggerFactory.getLogger(EvidenceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public EvidenceClient(RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          @Value("${evidence.baseUrl:http://localhost:8083}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public UUID createEvidence(String type, Object payload) {
        String url = baseUrl + "/evidence";

        Map<String, Object> request = Map.of(
            "type", type,
            "payload", payload
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("evidenceId")) {
                String evidenceId = (String) response.getBody().get("evidenceId");
                return UUID.fromString(evidenceId);
            }

            throw new RuntimeException("No evidenceId in response");

        } catch (Exception e) {
            log.error("Failed to create evidence: {}", e.getMessage());
            throw new RuntimeException("Failed to create evidence", e);
        }
    }

    public UUID createBundle(String bundleType, List<UUID> evidenceIds) {
        String url = baseUrl + "/evidence/bundles";

        Map<String, Object> request = Map.of(
            "type", bundleType,
            "evidenceIds", evidenceIds
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("bundleId")) {
                String bundleId = (String) response.getBody().get("bundleId");
                return UUID.fromString(bundleId);
            }

            throw new RuntimeException("No bundleId in response");

        } catch (Exception e) {
            log.error("Failed to create bundle: {}", e.getMessage());
            throw new RuntimeException("Failed to create bundle", e);
        }
    }

    /**
     * Update an evidence bundle with new evidence IDs
     * PUT /evidence/bundles/{bundleId}
     */
    public void updateBundle(UUID bundleId, List<UUID> evidenceIds) {
        String url = baseUrl + "/evidence/bundles/" + bundleId;

        Map<String, Object> request = Map.of(
                "evidenceIds", evidenceIds
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                Void.class
            );
        } catch (Exception e) {
            log.error("Failed to update bundle: {}", e.getMessage());
            throw new RuntimeException("Failed to update bundle", e);
        }
    }

    /**
     * Store an evidence artifact using multipart upload
     * POST /evidence/artifacts
     */
    public EvidenceArtifactResponse createArtifact(
            UUID tenantId,
            String type,
            String correlationId,
            String filename,
            String contentType,
            long sizeBytes,
            String sha256,
            Resource fileResource) {
        try {
            String url = baseUrl + "/evidence/artifacts";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("tenantId", tenantId.toString());
            body.add("type", type);
            body.add("correlationId", correlationId);
            body.add("filename", filename);
            body.add("contentType", contentType);
            body.add("sizeBytes", String.valueOf(sizeBytes));
            body.add("sha256", sha256);
            body.add("file", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if ((response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED)
                    && response.getBody() != null) {
                Object artifactRef = response.getBody().get("artifactRef");
                Object returnedHash = response.getBody().get("sha256");
                if (artifactRef != null) {
                    return new EvidenceArtifactResponse(
                            artifactRef.toString(),
                            returnedHash != null ? returnedHash.toString() : sha256
                    );
                }
            }

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to parse artifact response from evidence service");
        } catch (HttpClientErrorException e) {
            log.error("Evidence service rejected artifact payload", e);
            throw new ResponseStatusException(e.getStatusCode(), "Evidence service rejected artifact", e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Evidence service unavailable", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence service unavailable", e);
        } catch (RestClientException e) {
            log.error("Error creating artifact in evidence service", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }

    public record EvidenceArtifactResponse(String artifactRef, String sha256) {
    }
}
