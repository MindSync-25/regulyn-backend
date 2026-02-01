package io.regulyn.scanner.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.scanner.adapter.model.Finding;
import io.regulyn.scanner.model.ScanSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component("HTTP_DISCOVERY")
public class HttpDiscoveryScanAdapter implements ScanAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpDiscoveryScanAdapter.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpDiscoveryScanAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Finding> runInventory(ScanSource source, Instant since) {
        return runDiscovery(source, since, "inventory");
    }

    @Override
    public List<Finding> runRetentionCandidates(ScanSource source, Instant since) {
        return runDiscovery(source, since, "retention");
    }

    @SuppressWarnings("unchecked")
    private List<Finding> runDiscovery(ScanSource source, Instant since, String mode) {
        if (source.getBaseUrl() == null || source.getBaseUrl().isBlank()) {
            log.warn("HTTP_DISCOVERY source {} has no baseUrl", source.getSourceId());
            return Collections.emptyList();
        }

        String url = source.getBaseUrl() + "/discover";
        if (since != null) {
            url += "?since=" + DateTimeFormatter.ISO_INSTANT.format(since);
        }

        HttpHeaders headers = new HttpHeaders();
        applyAuth(headers, source);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            if (response.getBody() == null) {
                return Collections.emptyList();
            }

            return parseDiscoveryResponse(response.getBody(), mode);

        } catch (Exception e) {
            log.error("Failed to call HTTP_DISCOVERY endpoint: {}", url, e);
            throw new RuntimeException("HTTP discovery failed: " + e.getMessage(), e);
        }
    }

    private void applyAuth(HttpHeaders headers, ScanSource source) {
        String authType = source.getAuthType();
        String authRef = source.getAuthRef();

        if ("API_KEY".equals(authType) && authRef != null) {
            // In real implementation, authRef would be env var key or secret manager key
            // For now, use authRef directly as the key
            headers.set("X-API-Key", authRef);
        } else if ("BEARER".equals(authType) && authRef != null) {
            headers.set("Authorization", "Bearer " + authRef);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Finding> parseDiscoveryResponse(Map<String, Object> body, String mode) {
        List<Finding> findings = new ArrayList<>();

        if ("inventory".equals(mode)) {
            // Parse entities
            List<Map<String, Object>> entities = (List<Map<String, Object>>) body.get("entities");
            if (entities != null) {
                for (Map<String, Object> entity : entities) {
                    Finding finding = new Finding();
                    finding.setFindingType("ENTITY");
                    finding.setEntityType((String) entity.get("entityType"));
                    finding.setRiskLevel((String) entity.getOrDefault("riskLevel", "LOW"));
                    finding.setConfidence((Integer) entity.getOrDefault("confidence", 80));
                    finding.setDetails((Map<String, Object>) entity.getOrDefault("details", new HashMap<>()));
                    findings.add(finding);
                }
            }

            // Parse fields
            List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    Finding finding = new Finding();
                    finding.setFindingType("FIELD");
                    finding.setEntityType((String) field.get("entityType"));
                    finding.setFieldName((String) field.get("fieldName"));
                    finding.setDataCategory((String) field.get("dataCategory"));
                    finding.setRiskLevel((String) field.getOrDefault("riskLevel", "LOW"));
                    finding.setConfidence((Integer) field.getOrDefault("confidence", 80));
                    finding.setDetails((Map<String, Object>) field.getOrDefault("details", new HashMap<>()));
                    findings.add(finding);
                }
            }
        } else if ("retention".equals(mode)) {
            // Parse retention candidates
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("retentionCandidates");
            if (candidates != null) {
                for (Map<String, Object> candidate : candidates) {
                    Finding finding = new Finding();
                    finding.setFindingType("RETENTION_CANDIDATE");
                    finding.setEntityType((String) candidate.get("entityType"));
                    finding.setSubjectId(UUID.fromString((String) candidate.get("subjectId")));
                    finding.setRiskLevel((String) candidate.getOrDefault("riskLevel", "MED"));
                    finding.setConfidence((Integer) candidate.getOrDefault("confidence", 75));
                    finding.setDetails((Map<String, Object>) candidate.getOrDefault("details", new HashMap<>()));
                    findings.add(finding);
                }
            }
        }

        return findings;
    }
}
