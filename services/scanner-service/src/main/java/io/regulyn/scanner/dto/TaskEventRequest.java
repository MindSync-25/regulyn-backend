package io.regulyn.scanner.dto;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TaskEventRequest {

    public enum EventType {
        NOTE_ADDED,
        ATTACHMENT_ADDED
    }

    @NotNull(message = "eventType is required")
    private EventType eventType;

    private String notes;

    private List<String> attachmentRefs = new ArrayList<>();

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getAttachmentRefs() {
        return attachmentRefs;
    }

    public void setAttachmentRefs(List<String> attachmentRefs) {
        this.attachmentRefs = attachmentRefs;
    }
}
