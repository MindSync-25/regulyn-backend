package io.regulyn.connector.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Component("MOCK")
public class MockConnectorAdapter implements ConnectorAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockConnectorAdapter.class);
    private final ObjectMapper objectMapper;

    public MockConnectorAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ConnectorExecutionResult executeDelete(Connector connector, ConnectorJob job) {
        log.info("Executing MOCK delete for job {}", job.getJobId());

        Map<String, Object> response = new HashMap<>();
        response.put("deleted", true);
        response.put("jobId", job.getJobId().toString());
        response.put("subjectId", job.getSubjectId().toString());

        ConnectorExecutionResult result = new ConnectorExecutionResult();
        result.setStatus("SUCCEEDED");
        result.setResponseJson(response);
        result.setResultHash(computeHash(response));

        return result;
    }

    @Override
    public ConnectorExecutionResult executeExport(Connector connector, ConnectorJob job) {
        log.info("Executing MOCK export for job {}", job.getJobId());

        Map<String, Object> response = new HashMap<>();
        response.put("exported", true);
        response.put("items", 42);
        response.put("jobId", job.getJobId().toString());

        ConnectorExecutionResult result = new ConnectorExecutionResult();
        result.setStatus("SUCCEEDED");
        result.setResponseJson(response);
        result.setResultHash(computeHash(response));

        return result;
    }

    @Override
    public ConnectorExecutionResult executeAuditPull(Connector connector, ConnectorJob job) {
        log.info("Executing MOCK audit pull for job {}", job.getJobId());

        Map<String, Object> response = new HashMap<>();
        response.put("eventsPulled", 100);
        response.put("jobId", job.getJobId().toString());

        ConnectorExecutionResult result = new ConnectorExecutionResult();
        result.setStatus("SUCCEEDED");
        result.setResponseJson(response);
        result.setResultHash(computeHash(response));

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
