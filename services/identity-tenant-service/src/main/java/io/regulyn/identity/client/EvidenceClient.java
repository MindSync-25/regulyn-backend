package io.regulyn.identity.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private final RestTemplate restTemplate;
    private final String evidenceServiceUrl;

    public EvidenceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8083}") String evidenceServiceUrl) {
        this.restTemplate = restTemplate;
        this.evidenceServiceUrl = evidenceServiceUrl;
    }

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
}
