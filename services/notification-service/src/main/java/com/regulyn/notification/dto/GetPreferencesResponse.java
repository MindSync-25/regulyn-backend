package com.regulyn.notification.dto;

import java.util.List;

public record GetPreferencesResponse(
    String dataPrincipalId,
    List<Preference> preferences
) {
    public record Preference(
        String channel,
        String category,
        boolean optedOut
    ) {
    }
}
