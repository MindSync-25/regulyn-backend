package io.regulyn.scanner.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class TaskTransitionRequest {

    public enum Status {
        OPEN,
        IN_PROGRESS,
        CLOSED,
        WAIVED
    }

    @NotNull(message = "toStatus is required")
    private Status toStatus;

    private String notes;

    private UUID ownerUserId;

    private String ownerEmail;

    private String waivedReason;

    private String closureNotes;

    public Status getToStatus() {
        return toStatus;
    }

    public void setToStatus(Status toStatus) {
        this.toStatus = toStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getWaivedReason() {
        return waivedReason;
    }

    public void setWaivedReason(String waivedReason) {
        this.waivedReason = waivedReason;
    }

    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }
}
