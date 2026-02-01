package com.regulyn.guardian.dto;

import java.util.UUID;

public record CreateExportResponse(
    UUID bundleId,
    UUID exportId,
    String downloadPath
) {}
