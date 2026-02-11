package com.regulyn.guardian.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class EvidenceReportingClient {

    private final RestTemplate restTemplate;
    private final String evidenceReportingBaseUrl;

    public EvidenceReportingClient(
            RestTemplate restTemplate,
            @Value("${evidence.reporting.baseUrl:http://localhost:8087}") String evidenceReportingBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.evidenceReportingBaseUrl = evidenceReportingBaseUrl;
    }

    public EvidenceBundleResponse createBundle(Map<String, Object> payload) {
        String url = evidenceReportingBaseUrl + "/evidence/bundles";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object bundleRef = response.getBody().get("bundleRef");
                Object bundleSha = response.getBody().get("bundleSha256");
                if (bundleRef != null && bundleSha != null) {
                    return new EvidenceBundleResponse(bundleRef.toString(), bundleSha.toString());
                }
            }

            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence reporting service failed to create bundle"
            );
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence reporting service unavailable: " + e.getMessage(),
                    e
            );
        }
    }

    public record EvidenceBundleResponse(String bundleRef, String bundleSha256) {
    }
}
