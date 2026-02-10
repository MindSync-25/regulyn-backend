package com.regulyn.ropa.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceReportingClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String evidenceBaseUrl;

    public EvidenceReportingClient(RestTemplate restTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${evidence.base-url:http://localhost:8083}") String evidenceBaseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.evidenceBaseUrl = evidenceBaseUrl;
    }

    public EvidenceArtifactResponse createArtifact(UUID tenantId,
                                                   UUID userId,
                                                   String idempotencyKey,
                                                   String artifactType,
                                                   String filename,
                                                   String contentType,
                                                   Map<String, Object> payload) {
        try {
            String url = evidenceBaseUrl + "/evidence/artifacts";

            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            String sha256 = sha256(bytes);

            Map<String, Object> body = Map.of(
                "tenantId", tenantId.toString(),
                "type", artifactType,
                "filename", filename,
                "contentType", contentType,
                "sha256", sha256,
                "bytes", Base64.getEncoder().encodeToString(bytes)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId.toString());
            headers.set("X-User-ID", userId.toString());
            headers.set("X-Idempotency-Key", idempotencyKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
                throw new RuntimeException("Failed to store evidence artifact: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("artifactRef")) {
                throw new RuntimeException("Invalid response from evidence service");
            }

            String artifactRef = responseBody.get("artifactRef").toString();
            String artifactHash = responseBody.containsKey("artifactHash")
                ? responseBody.get("artifactHash").toString()
                : responseBody.getOrDefault("sha256", sha256).toString();

            return new EvidenceArtifactResponse(artifactRef, artifactHash, sha256);
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to store evidence artifact", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build evidence artifact payload", e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }

    public record EvidenceArtifactResponse(String artifactRef, String artifactHash, String payloadHash) {
    }
}
