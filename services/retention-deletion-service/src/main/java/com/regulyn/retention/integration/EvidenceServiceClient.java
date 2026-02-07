package com.regulyn.retention.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            List<String> artifactHashes,
            Map<String, Object> metadata) {

        CreateEvidenceRequest request = new CreateEvidenceRequest();
        request.setAction(action);
        request.setReferenceId(deletionId);
        request.setSubjectId(subjectId);
        request.setEntityType(entityType);
        request.setStatus(status);
        request.setArtifactHashes(artifactHashes);
        request.setMetadata(metadata);

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
            List<UUID> evidenceIds,
            Map<String, Object> metadata) {

        CreateBundleRequest request = new CreateBundleRequest();
        request.setBundleType(bundleType);
        request.setReferenceType(referenceType);
        request.setReferenceId(referenceId);
        request.setEvidenceIds(evidenceIds);
        request.setMetadata(metadata);

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

    public CreateArtifactResponse createArtifact(
            UUID tenantId,
            UUID userId,
            String artifactType,
            String referenceType,
            UUID referenceId,
            String sha256,
            String storageRef,
            Map<String, Object> additionalMetadata) {

        CreateArtifactRequest request = new CreateArtifactRequest();
        request.setUserId(userId != null ? userId.toString() : null);
        request.setEventType("deletion.artifact_created");
        request.setEvidenceType(artifactType);
        request.setDescription(referenceType + ":" + referenceId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("artifactType", artifactType);
        metadata.put("referenceType", referenceType);
        metadata.put("referenceId", referenceId);
        metadata.put("sha256", sha256);
        if (storageRef != null) {
            metadata.put("storageRef", storageRef);
        }
        if (additionalMetadata != null && !additionalMetadata.isEmpty()) {
            metadata.putAll(additionalMetadata);
        }
        request.setMetadata(metadata);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", userId.toString());

        HttpEntity<CreateArtifactRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            CreateArtifactResponse response = restTemplate.postForObject(
                    evidenceBaseUrl + "/evidence",
                    httpEntity,
                    CreateArtifactResponse.class);
            if (response == null || response.resolveArtifactId() == null) {
                throw new IllegalStateException("Evidence service returned no artifact id");
            }
            if (response.getArtifactId() == null) {
                response.setArtifactId(response.getEvidenceId());
            }
            return response;
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
