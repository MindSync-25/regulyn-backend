package com.regulyn.retention.connector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class ConnectorServiceClient {

    private final RestTemplate restTemplate;
    private final String connectorBaseUrl;

    public ConnectorServiceClient(
            RestTemplate restTemplate,
            @Value("${connector.service.url:http://localhost:8084}") String connectorBaseUrl) {
        this.restTemplate = restTemplate;
        this.connectorBaseUrl = connectorBaseUrl;
    }

    public StartDeletionJobResponse startDeletionJob(
            UUID tenantId,
            UUID actorId,
            String idempotencyKey,
            StartDeletionJobRequest request) {

        HttpHeaders headers = buildHeaders(tenantId, actorId, idempotencyKey);
        HttpEntity<StartDeletionJobRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<StartDeletionJobResponse> response = restTemplate.exchange(
                    connectorBaseUrl + "/connectors/deletions/jobs",
                    HttpMethod.POST,
                    entity,
                    StartDeletionJobResponse.class
            );
            return response.getBody();
        } catch (ResourceAccessException e) {
            throw new ConnectorServiceUnavailableException("Connector service unavailable", e);
        } catch (HttpServerErrorException e) {
            throw new ConnectorServiceUnavailableException("Connector service error: " + e.getStatusCode(), e);
        } catch (HttpClientErrorException e) {
            throw new ConnectorServiceClientException("Connector service client error: " + e.getStatusCode(), e, e.getStatusCode().value());
        }
    }

    public GetDeletionJobStatusResponse getDeletionJobStatus(
            UUID tenantId,
            UUID actorId,
            String idempotencyKey,
            String jobId) {

        HttpHeaders headers = buildHeaders(tenantId, actorId, idempotencyKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<GetDeletionJobStatusResponse> response = restTemplate.exchange(
                    connectorBaseUrl + "/connectors/deletions/jobs/" + jobId,
                    HttpMethod.GET,
                    entity,
                    GetDeletionJobStatusResponse.class
            );
            return response.getBody();
        } catch (ResourceAccessException e) {
            throw new ConnectorServiceUnavailableException("Connector service unavailable", e);
        } catch (HttpServerErrorException e) {
            throw new ConnectorServiceUnavailableException("Connector service error: " + e.getStatusCode(), e);
        } catch (HttpClientErrorException e) {
            throw new ConnectorServiceClientException("Connector service client error: " + e.getStatusCode(), e, e.getStatusCode().value());
        }
    }

    private HttpHeaders buildHeaders(UUID tenantId, UUID actorId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set("X-Tenant-ID", tenantId.toString());
        }
        if (actorId != null) {
            headers.set("X-User-ID", actorId.toString());
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
