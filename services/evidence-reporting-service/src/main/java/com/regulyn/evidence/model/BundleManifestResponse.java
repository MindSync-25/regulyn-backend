package com.regulyn.evidence.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BundleManifestResponse(
    UUID bundleId,
    String bundleType,
    String referenceType,
    String referenceId,
    String title,
    String description,
    String bundleHash,
    String status,
    Instant createdAt,
    UUID createdBy,
    List<BundleItem> items,
    Map<String, Object> metadata
) {
    public record BundleItem(
        UUID itemId,
        String itemType,  // EVIDENCE or ARTIFACT
        UUID evidenceId,
        UUID artifactId,
        String itemHash,
        Map<String, Object> itemMeta
    ) {}
}
