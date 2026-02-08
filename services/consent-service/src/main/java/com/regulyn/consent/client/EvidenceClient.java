package com.regulyn.consent.client;

import java.util.Map;
import java.util.UUID;

public interface EvidenceClient {
    UUID createArtifact(UUID tenantId, String artifactType, Map<String, Object> metadata);
}
