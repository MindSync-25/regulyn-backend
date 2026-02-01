package io.regulyn.connector.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Component("WEBHOOK_HTTP")
public class WebhookHttpConnectorAdapter implements ConnectorAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpConnectorAdapter.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WebhookHttpConnectorAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConnectorExecutionResult executeDelete(Connector connector, ConnectorJob job) {
        return executeWebhook(connector, job, "DELETE");
    }

    @Override
    public ConnectorExecutionResult executeExport(Connector connector, ConnectorJob job) {
        return executeWebhook(connector, job, "EXPORT");
    }

    @Override
    public ConnectorExecutionResult executeAuditPull(Connector connector, ConnectorJob job) {
        return executeWebhook(connector, job, "AUDIT_PULL");
    }

    private ConnectorExecutionResult executeWebhook(Connector connector, ConnectorJob job, String action) {
        ConnectorExecutionResult result = new ConnectorExecutionResult();

        try {
            if (connector.getBaseUrl() == null || connector.getBaseUrl().isBlank()) {
                result.setStatus("FAILED");
                result.setError("Base URL is not configured for connector");
                return result;
            }

            String url = connector.getBaseUrl() + "/execute";

            // Build request payload
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("action", action);
            requestPayload.put("jobId", job.getJobId().toString());
            requestPayload.put("targetId", job.getTargetId().toString());
            requestPayload.put("subjectId", job.getSubjectId().toString());
            requestPayload.put("subjectType", job.getSubjectType());
            requestPayload.put("payload", job.getPayload());

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add authentication if configured
            String authRef = connector.getAuthRef();
            if (authRef != null && !authRef.isBlank()) {
                String authValue = System.getenv(authRef); // Lookup from environment
                if (authValue != null) {
                    if ("API_KEY".equals(connector.getAuthType())) {
                        headers.set("X-API-Key", authValue);
                    } else if ("BEARER".equals(connector.getAuthType())) {
                        headers.set("Authorization", "Bearer " + authValue);
                    }
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestPayload, headers);

            log.info("Calling webhook: POST {} for job {}", url, job.getJobId());

            // Make HTTP call
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                responseBody = new HashMap<>();
            }

            result.setStatus("SUCCEEDED");
            result.setResponseJson(responseBody);
            result.setResultHash(computeHash(responseBody));

            log.info("Webhook call succeeded for job {}", job.getJobId());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error calling webhook for job {}: {}", job.getJobId(), e.getMessage());
            result.setStatus("FAILED");
            result.setError("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error calling webhook for job {}", job.getJobId(), e);
            result.setStatus("FAILED");
            result.setError(e.getMessage());
        }

        return result;
    }

    private String computeHash(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute hash", e);
            return "error-computing-hash";
        }
    }
}
