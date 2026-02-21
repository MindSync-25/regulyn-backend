package com.regulyn.retention.client;

import com.regulyn.auth.client.IdentityAuthClient;
import com.regulyn.auth.client.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * REST client implementation for validating API keys with identity-tenant-service.
 * Uses Spring 6 RestClient for synchronous HTTP calls.
 */
@Component
public class IdentityAuthClientImpl implements IdentityAuthClient {

  private static final Logger log = LoggerFactory.getLogger(IdentityAuthClientImpl.class);

  private final RestClient restClient;
  private final String internalAuthToken;

  public IdentityAuthClientImpl(
      @Value("${identity.baseUrl:http://localhost:8081}") String identityBaseUrl,
      @Value("${internal.auth.token:change-me-in-production}") String internalAuthToken) {
    this.internalAuthToken = internalAuthToken;
    this.restClient = RestClient.builder()
        .baseUrl(identityBaseUrl)
        .build();
  }

  @Override
  public ValidationResult validateApiKey(String rawApiKey) {
    try {
      // NEVER log raw API keys
      log.debug("Validating API key with identity-tenant-service");

      @SuppressWarnings("rawtypes")
      Map<String, Object> response = restClient.post()
          .uri("/internal/api-keys/validate")
          .header("X-Internal-Auth", internalAuthToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("apiKey", rawApiKey))
          .retrieve()
          .onStatus(HttpStatusCode::is4xxClientError, (request, responseEntity) ->
              log.debug("API key validation failed with status: {}", responseEntity.getStatusCode()))
          .onStatus(HttpStatusCode::is5xxServerError, (request, responseEntity) ->
              log.error("Identity service error: {}", responseEntity.getStatusCode()))
          .body(Map.class);

      if (response != null && Boolean.TRUE.equals(response.get("valid"))) {
        UUID tenantId = UUID.fromString((String) response.get("tenantId"));
        UUID userId = response.get("userId") != null ? UUID.fromString((String) response.get("userId")) : null;
        @SuppressWarnings("unchecked")
        var roles = (java.util.List<String>) response.get("roles");

        log.debug("API key validation successful for tenant: {}", tenantId);
        return ValidationResult.valid(tenantId, userId, roles);
      }
    } catch (Exception e) {
      log.error("Failed to validate API key with identity service", e);
    }

    return ValidationResult.invalid();
  }
}
