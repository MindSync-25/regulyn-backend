package io.regulyn.scanner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class RemediationTaskResponse {

    private UUID taskId;
    private UUID sourceId;
    private UUID runId;
    private UUID findingPk;
    private String findingFingerprint;
    private String title;
    private String severity;
    private UUID ownerUserId;
    private String ownerEmail;
    private LocalDate dueDate;
    private String status;
    private String closureNotes;
    private String closureNotesHash;
    private String waivedReason;
    private Instant closedAt;
    private UUID closedByUserId;
    private String evidenceArtifactRef;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public UUID getFindingPk() {
        return findingPk;
    }

    public void setFindingPk(UUID findingPk) {
        this.findingPk = findingPk;
    }

    public String getFindingFingerprint() {
        return findingFingerprint;
    }

    public void setFindingFingerprint(String findingFingerprint) {
        this.findingFingerprint = findingFingerprint;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }

    public String getClosureNotesHash() {
        return closureNotesHash;
    }

    public void setClosureNotesHash(String closureNotesHash) {
        this.closureNotesHash = closureNotesHash;
    }

    public String getWaivedReason() {
        return waivedReason;
    }

    public void setWaivedReason(String waivedReason) {
        this.waivedReason = waivedReason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public UUID getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(UUID closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public String getEvidenceArtifactRef() {
        return evidenceArtifactRef;
    }

    public void setEvidenceArtifactRef(String evidenceArtifactRef) {
        this.evidenceArtifactRef = evidenceArtifactRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
