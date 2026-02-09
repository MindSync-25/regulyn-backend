package io.regulyn.scanner.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "remediation_tasks", schema = "scanner")
public class RemediationTaskEntity {

    public enum Severity {
        LOW,
        MED,
        HIGH,
        CRITICAL
    }

    public enum Status {
        OPEN,
        IN_PROGRESS,
        CLOSED,
        WAIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "finding_pk")
    private UUID findingPk;

    @Column(name = "finding_fingerprint", nullable = false, length = 64)
    private String findingFingerprint;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "closure_notes")
    private String closureNotes;

    @Column(name = "closure_notes_hash", length = 64)
    private String closureNotesHash;

    @Column(name = "waived_reason")
    private String waivedReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

    @Column(name = "evidence_artifact_ref")
    private String evidenceArtifactRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
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