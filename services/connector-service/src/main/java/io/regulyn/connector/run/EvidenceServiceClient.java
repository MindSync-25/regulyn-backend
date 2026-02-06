package io.regulyn.connector.run;

import java.util.Map;

/**
 * Client for evidence-reporting-service artifacts.
 */
public interface EvidenceServiceClient {

    /**
     * Create an evidence artifact and return its reference.
     */
    String createArtifact(Map<String, Object> payload);
}
