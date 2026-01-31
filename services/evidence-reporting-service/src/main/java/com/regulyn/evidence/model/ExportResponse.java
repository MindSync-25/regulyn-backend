package com.regulyn.evidence.model;

import java.util.UUID;

public record ExportResponse(
    UUID exportId,
    String status,
    String downloadPath,
    String exportHash
) {}
