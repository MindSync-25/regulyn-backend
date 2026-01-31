package com.regulyn.evidence.model;

import java.util.List;
import java.util.UUID;

public record VerifyResponse(
    UUID bundleId,
    boolean valid,
    List<String> problems
) {}
