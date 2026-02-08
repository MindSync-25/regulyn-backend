package com.regulyn.consent.client;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NoopEvidenceClient implements EvidenceClient {
    @Override
    public UUID createArtifact(UUID tenantId, String artifactType, Map<String, Object> metadata) {
        return null;
    }
}
