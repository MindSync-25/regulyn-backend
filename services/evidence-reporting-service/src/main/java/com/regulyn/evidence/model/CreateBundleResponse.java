package com.regulyn.evidence.model;

import java.util.UUID;

public record CreateBundleResponse(
    UUID bundleId,
    String bundleHash,
    String status
) {}
