package io.regulyn.scanner.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class RetentionDeletionClient {

    private static final Logger log = LoggerFactory.getLogger(RetentionDeletionClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RetentionDeletionClient(RestTemplate restTemplate,
                                    @Value("${retention.baseUrl:http://localhost:8086}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void registerCandidate(UUID subjectId, String subjectType, String entityType) {
        String url = baseUrl + "/retention/candidates";

        Map<String, Object> request = Map.of(
            "subjectId", subjectId.toString(),
            "subjectType", subjectType,
            "entityType", entityType
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Void.class
            );
            log.debug("Registered retention candidate: {}", subjectId);
        } catch (Exception e) {
            log.error("Failed to register retention candidate {}: {}", subjectId, e.getMessage());
            throw new RuntimeException("Failed to register retention candidate", e);
        }
    }
}
