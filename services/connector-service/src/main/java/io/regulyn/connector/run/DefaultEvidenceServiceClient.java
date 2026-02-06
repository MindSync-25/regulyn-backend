package io.regulyn.connector.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Default implementation of evidence client.
 */
@Component
public class DefaultEvidenceServiceClient implements EvidenceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultEvidenceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String evidenceBaseUrl;

    public DefaultEvidenceServiceClient(RestTemplate restTemplate,
                                        @Value("${regulyn.evidence.baseUrl}") String evidenceBaseUrl) {
        this.restTemplate = restTemplate;
        this.evidenceBaseUrl = evidenceBaseUrl;
    }

    @Override
    public String createArtifact(Map<String, Object> payload) {
        try {
            String url = evidenceBaseUrl + "/api/v1/evidence/artifacts";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            log.info("Creating evidence artifact via POST {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("artifact_ref")) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Invalid response from evidence service");
            }

            return body.get("artifact_ref").toString();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create evidence artifact", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }
}
