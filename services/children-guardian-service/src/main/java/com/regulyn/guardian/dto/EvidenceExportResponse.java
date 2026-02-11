package com.regulyn.guardian.dto;

import java.util.UUID;

public record EvidenceExportResponse(
        UUID exportId,
        String status,
        String bundleRef,
        String bundleSha256
) {}
