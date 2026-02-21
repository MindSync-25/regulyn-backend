package io.regulyn.identity.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

import io.regulyn.identity.dto.EvidenceBundlePageResponse;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EvidenceClient {

    private final RestTemplate restTemplate;
    private final String evidenceServiceUrl;

    public EvidenceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8095}") String evidenceServiceUrl) {
        this.restTemplate = restTemplate;
        this.evidenceServiceUrl = evidenceServiceUrl;
    }

    public UUID createEvidence(Map<String, Object> evidenceData) throws RestClientException {
        String url = evidenceServiceUrl + "/evidence";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Get Authorization and X-Tenant-Id headers from current request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            String tenantIdHeader = request.getHeader("X-Tenant-Id");
            if (tenantIdHeader != null) {
                headers.set("X-Tenant-Id", tenantIdHeader);
            }
        }
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(evidenceData, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                request, 
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("evidenceId")) {
            return UUID.fromString(body.get("evidenceId").toString());
        }
        throw new RuntimeException("Failed to create evidence: no evidenceId in response");
    }

    public EvidenceBundlePageResponse listBundles(UUID tenantId, int page, int size) throws RestClientException {
        String url = UriComponentsBuilder
                .fromHttpUrl(evidenceServiceUrl)
                .path("/admin/tenants/")
                .path(tenantId.toString())
                .path("/evidence/bundles")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();

        // Get Authorization header from current request
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
        }

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<EvidenceBundlePageResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                EvidenceBundlePageResponse.class
        );

        return response.getBody();
    }
}
