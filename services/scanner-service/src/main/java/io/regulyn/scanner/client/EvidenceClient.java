package io.regulyn.scanner.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        return createEvidence(type, payload, Map.of());
    }

    public UUID createEvidence(String type, Object payload, Map<String, String> extraHeaders) {
        String url = baseUrl + "/evidence";

        Map<String, Object> request = Map.of(
            "type", type,
            "payload", payload
        );

        HttpHeaders headers = buildHeaders(extraHeaders);
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

    public UUID createBundle(String bundleType, UUID evidenceId) {
        return createBundle(bundleType, evidenceId, Map.of());
    }

    public UUID createBundle(String bundleType, UUID evidenceId, Map<String, String> extraHeaders) {
        String url = baseUrl + "/evidence/bundles";

        Map<String, Object> request = Map.of(
            "type", bundleType,
            "evidenceIds", new UUID[]{evidenceId}
        );

        HttpHeaders headers = buildHeaders(extraHeaders);
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

    public UUID createExport(UUID bundleId) {
        String url = baseUrl + "/evidence/bundles/" + bundleId + "/export";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("exportId")) {
                String exportId = (String) response.getBody().get("exportId");
                return UUID.fromString(exportId);
            }

            throw new RuntimeException("No exportId in response");

        } catch (Exception e) {
            log.error("Failed to create export: {}", e.getMessage());
            throw new RuntimeException("Failed to create export", e);
        }
    }

    public byte[] downloadExport(UUID exportId) {
        String url = baseUrl + "/evidence/exports/" + exportId + "/download";

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to download export: {}", e.getMessage());
            throw new RuntimeException("Failed to download export", e);
        }
    }

    private HttpHeaders buildHeaders(Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (extraHeaders != null) {
            extraHeaders.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.set(key, value);
                }
            });
        }
        return headers;
    }
}
