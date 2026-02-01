package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaActivityVersion;

import java.time.Instant;
import java.util.UUID;

public class ActivityResponse {

    private UUID activityId;
    private RopaActivityVersion.Status status;
    private Instant publishedAt;

    public ActivityResponse() {
    }

    public ActivityResponse(UUID activityId, RopaActivityVersion.Status status) {
        this.activityId = activityId;
        this.status = status;
    }

    public ActivityResponse(UUID activityId, RopaActivityVersion.Status status, Instant publishedAt) {
        this.activityId = activityId;
        this.status = status;
        this.publishedAt = publishedAt;
    }

    // Getters and Setters
    public UUID getActivityId() {
        return activityId;
    }

    public void setActivityId(UUID activityId) {
        this.activityId = activityId;
    }

    public RopaActivityVersion.Status getStatus() {
        return status;
    }

    public void setStatus(RopaActivityVersion.Status status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
