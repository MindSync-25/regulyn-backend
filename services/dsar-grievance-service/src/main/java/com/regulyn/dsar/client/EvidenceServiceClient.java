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

import java.util.Map;
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

    public CreateBundleResponse createBundleWithMetadata(String bundleType, String referenceType, String referenceId,
                                                         String title, String description, List<String> evidenceIds,
                                                         List<String> artifactIds, Map<String, Object> metadata) {
        TenantContext context = TenantContextHolder.getContext();

        CreateBundleRequest request = new CreateBundleRequest();
        request.setBundleType(bundleType);
        request.setReferenceType(referenceType);
        request.setReferenceId(referenceId);
        request.setTitle(title);
        request.setDescription(description);
        request.setEvidenceIds(evidenceIds);
        request.setArtifactIds(artifactIds);
        request.setMetadata(metadata);

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

            return response.getBody();

        } catch (RestClientException e) {
            throw new RuntimeException("Failed to create evidence bundle: " + e.getMessage(), e);
        }
    }

    public String storeAttachmentArtifact(UUID tenantId, UUID dsarId, String filename,
                                          String contentType, long sizeBytes, String sha256, byte[] bytes) {
        TenantContext context = TenantContextHolder.getContext();

        EvidenceRequest request = new EvidenceRequest();
        request.setUserId(context.getUserId().toString());
        request.setEventType("DSAR_ATTACHMENT_UPLOAD");
        request.setEvidenceType("DSAR_ATTACHMENT_UPLOAD");
        request.setDescription("DSAR attachment uploaded");
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("tenantId", tenantId.toString());
        metadata.put("dsarId", dsarId.toString());
        metadata.put("sizeBytes", sizeBytes);
        metadata.put("sha256", sha256);
        if (filename != null) {
            metadata.put("filename", filename);
        }
        if (contentType != null) {
            metadata.put("contentType", contentType);
        }
        request.setMetadata(metadata);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", context.getTenantId().toString());
        headers.set("X-User-ID", context.getUserId().toString());
        headers.set("Content-Type", "application/json");

        HttpEntity<EvidenceRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<EvidenceResponse> response = restTemplate.exchange(
                evidenceServiceBaseUrl + "/evidence",
                HttpMethod.POST,
                httpEntity,
                EvidenceResponse.class
            );

            if (response.getBody() == null || response.getBody().getEvidenceId() == null) {
                throw new RuntimeException("Evidence service returned null response");
            }

            return "evidence://" + response.getBody().getEvidenceId();

        } catch (RestClientException e) {
            throw new RuntimeException("Failed to store attachment artifact: " + e.getMessage(), e);
        }
    }

    public String storeReferenceRecordedArtifact(UUID tenantId, UUID dsarId, Map<String, Object> metadata) {
        TenantContext context = TenantContextHolder.getContext();

        EvidenceRequest request = new EvidenceRequest();
        request.setUserId(context.getUserId().toString());
        request.setEventType("DSAR_ATTACHMENT_REFERENCE_RECORDED");
        request.setEvidenceType("DSAR_ATTACHMENT_REFERENCE");
        request.setDescription("DSAR attachment reference recorded");
        request.setMetadata(metadata);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", context.getTenantId().toString());
        headers.set("X-User-ID", context.getUserId().toString());
        headers.set("Content-Type", "application/json");

        HttpEntity<EvidenceRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<EvidenceResponse> response = restTemplate.exchange(
                evidenceServiceBaseUrl + "/evidence",
                HttpMethod.POST,
                httpEntity,
                EvidenceResponse.class
            );

            if (response.getBody() == null || response.getBody().getEvidenceId() == null) {
                throw new RuntimeException("Evidence service returned null response");
            }

            return "evidence://" + response.getBody().getEvidenceId();

        } catch (RestClientException e) {
            throw new RuntimeException("Failed to store reference artifact: " + e.getMessage(), e);
        }
    }

    public String storeCloseEvidence(UUID tenantId, UUID dsarId, UUID closeEventId, Map<String, Object> metadata) {
        TenantContext context = TenantContextHolder.getContext();

        EvidenceRequest request = new EvidenceRequest();
        request.setUserId(context.getUserId().toString());
        request.setEventType("DSAR_CLOSE_EVIDENCE");
        request.setEvidenceType("DSAR_CLOSE_EVIDENCE");
        request.setDescription("DSAR close evidence bundle summary");
        Map<String, Object> enriched = new java.util.HashMap<>();
        enriched.put("tenantId", tenantId.toString());
        enriched.put("dsarId", dsarId.toString());
        enriched.put("closeEventId", closeEventId.toString());
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        request.setMetadata(enriched);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", context.getTenantId().toString());
        headers.set("X-User-ID", context.getUserId().toString());
        headers.set("Content-Type", "application/json");

        HttpEntity<EvidenceRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<EvidenceResponse> response = restTemplate.exchange(
                evidenceServiceBaseUrl + "/evidence",
                HttpMethod.POST,
                httpEntity,
                EvidenceResponse.class
            );

            if (response.getBody() == null || response.getBody().getEvidenceId() == null) {
                throw new RuntimeException("Evidence service returned null response");
            }

            return response.getBody().getEvidenceId();

        } catch (RestClientException e) {
            throw new RuntimeException("Failed to store close evidence: " + e.getMessage(), e);
        }
    }
}
