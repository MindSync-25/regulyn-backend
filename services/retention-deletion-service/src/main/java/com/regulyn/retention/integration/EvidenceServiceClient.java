package com.regulyn.retention.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Client for evidence-reporting-service integration.
 * Creates evidence records and bundles for deletion proof.
 */
@Component
public class EvidenceServiceClient {

    private final RestTemplate restTemplate;
    private final String evidenceBaseUrl;

    public EvidenceServiceClient(
            RestTemplate restTemplate,
            @Value("${evidence.service.url:http://localhost:8083}") String evidenceBaseUrl) {
        this.restTemplate = restTemplate;
        this.evidenceBaseUrl = evidenceBaseUrl;
    }

    public CreateEvidenceResponse createEvidence(
            UUID tenantId,
            UUID userId,
            String action,
            UUID deletionId,
            UUID subjectId,
            String entityType,
            String status,
            List<String> artifactHashes) {

        CreateEvidenceRequest request = new CreateEvidenceRequest();
        request.setAction(action);
        request.setReferenceId(deletionId);
        request.setSubjectId(subjectId);
        request.setEntityType(entityType);
        request.setStatus(status);
        request.setArtifactHashes(artifactHashes);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", userId.toString());

        HttpEntity<CreateEvidenceRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.postForObject(
                    evidenceBaseUrl + "/evidence",
                    httpEntity,
                    CreateEvidenceResponse.class);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new EvidenceServiceUnavailableException("Evidence service unavailable", e);
            }
            throw e;
        }
    }

    public CreateBundleResponse createBundle(
            UUID tenantId,
            UUID userId,
            String bundleType,
            String referenceType,
            UUID referenceId,
            List<UUID> evidenceIds) {

        CreateBundleRequest request = new CreateBundleRequest();
        request.setBundleType(bundleType);
        request.setReferenceType(referenceType);
        request.setReferenceId(referenceId);
        request.setEvidenceIds(evidenceIds);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", userId.toString());

        HttpEntity<CreateBundleRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.postForObject(
                    evidenceBaseUrl + "/bundles",
                    httpEntity,
                    CreateBundleResponse.class);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new EvidenceServiceUnavailableException("Evidence service unavailable", e);
            }
            throw e;
        }
    }

    public static class EvidenceServiceUnavailableException extends RuntimeException {
        public EvidenceServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
