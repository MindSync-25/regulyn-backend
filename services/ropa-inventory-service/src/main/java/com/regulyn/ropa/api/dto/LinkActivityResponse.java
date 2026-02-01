package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class LinkActivityResponse {

    private UUID activityId;
    private Boolean linked;

    public LinkActivityResponse() {
    }

    public LinkActivityResponse(UUID activityId, Boolean linked) {
        this.activityId = activityId;
        this.linked = linked;
    }

    // Getters and Setters
    public UUID getActivityId() {
        return activityId;
    }

    public void setActivityId(UUID activityId) {
        this.activityId = activityId;
    }

    public Boolean getLinked() {
        return linked;
    }

    public void setLinked(Boolean linked) {
        this.linked = linked;
    }
}
