package com.regulyn.dsar.client;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
public class EvidenceServiceClient {
    
    private final RestTemplate restTemplate;
    private final String evidenceServiceBaseUrl;
    
    public EvidenceServiceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8095}") String evidenceServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.evidenceServiceBaseUrl = evidenceServiceBaseUrl;
    }
    
    public UUID createBundle(String bundleType, String referenceType, String referenceId, 
                           String title, List<String> evidenceIds) {
        
        TenantContext context = TenantContextHolder.getContext();
        
        CreateBundleRequest request = new CreateBundleRequest();
        request.setBundleType(bundleType);
        request.setReferenceType(referenceType);
        request.setReferenceId(referenceId);
        request.setTitle(title);
        request.setEvidenceIds(evidenceIds);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", context.getTenantId().toString());
        headers.set("X-User-ID", context.getUserId().toString());
        headers.set("Content-Type", "application/json");
        
        HttpEntity<CreateBundleRequest> httpEntity = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<CreateBundleResponse> response = restTemplate.exchange(
                evidenceServiceBaseUrl + "/bundles",
                HttpMethod.POST,
                httpEntity,
                CreateBundleResponse.class
            );
            
            if (response.getBody() == null) {
                throw new RuntimeException("Evidence service returned null response");
            }
            
            return response.getBody().getBundleId();
            
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to create evidence bundle: " + e.getMessage(), e);
        }
    }
}
