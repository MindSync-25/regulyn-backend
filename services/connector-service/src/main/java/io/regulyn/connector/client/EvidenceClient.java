package io.regulyn.connector.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceClient {

    private static final Logger log = LoggerFactory.getLogger(EvidenceClient.class);

    private final RestTemplate restTemplate;
    private final String evidenceBaseUrl;
    private final ObjectMapper objectMapper;

    public EvidenceClient(RestTemplate restTemplate,
                         @Value("${regulyn.evidence.baseUrl}") String evidenceBaseUrl,
                         ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.evidenceBaseUrl = evidenceBaseUrl;
        this.objectMapper = objectMapper;
    }

    public UUID createEvidence(Map<String, Object> payload) {
        try {
            String url = evidenceBaseUrl + "/evidence";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Creating evidence via POST {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("evidenceId")) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Invalid response from evidence service");
            }
            
            return UUID.fromString(body.get("evidenceId").toString());
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create evidence", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }

    public UUID createBundle(Map<String, Object> bundleData) {
        try {
            String url = evidenceBaseUrl + "/bundles";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(bundleData, headers);
            
            log.info("Creating bundle via POST {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("bundleId")) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Invalid response from evidence service");
            }
            
            return UUID.fromString(body.get("bundleId").toString());
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create bundle", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }

    public UUID createExport(UUID bundleId) {
        try {
            String url = evidenceBaseUrl + "/bundles/" + bundleId + "/export";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Creating export via POST {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("exportId")) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Invalid response from evidence service");
            }
            
            return UUID.fromString(body.get("exportId").toString());
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create export", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }

    public byte[] downloadExport(UUID exportId) {
        try {
            String url = evidenceBaseUrl + "/exports/" + exportId + "/download";
            
            log.info("Downloading export via GET {}", url);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to download export {}", exportId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evidence service unavailable", e);
        }
    }
}
