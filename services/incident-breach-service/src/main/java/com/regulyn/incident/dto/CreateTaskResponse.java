package com.regulyn.incident.dto;

import java.util.UUID;

public record CreateTaskResponse(
    UUID taskId,
    String status
) {}
