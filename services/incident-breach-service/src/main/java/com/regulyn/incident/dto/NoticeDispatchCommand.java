package com.regulyn.incident.dto;

import java.util.List;

public record NoticeDispatchCommand(
    String recipientType,
    List<String> recipients,
    String segmentRef,
    String channel,
    String subject
) {
    public NoticeDispatchCommand {
        if (recipients == null) {
            recipients = List.of();
        }
    }
}
