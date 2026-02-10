package com.regulyn.employee.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_exit_workflows", schema = "employee")
public class EmployeeExitWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "terminated_at", nullable = false)
    private OffsetDateTime terminatedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "subject_ref")
    private String subjectRef;

    @Column(name = "retention_deletion_request_ref")
    private String retentionDeletionRequestRef;

    @Column(name = "disable_access_task_ref")
    private String disableAccessTaskRef;

    @Column(name = "evidence_bundle_ref")
    private String evidenceBundleRef;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (startedAt == null) {
            startedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public OffsetDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(OffsetDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public void setSubjectRef(String subjectRef) {
        this.subjectRef = subjectRef;
    }

    public String getRetentionDeletionRequestRef() {
        return retentionDeletionRequestRef;
    }

    public void setRetentionDeletionRequestRef(String retentionDeletionRequestRef) {
        this.retentionDeletionRequestRef = retentionDeletionRequestRef;
    }

    public String getDisableAccessTaskRef() {
        return disableAccessTaskRef;
    }

    public void setDisableAccessTaskRef(String disableAccessTaskRef) {
        this.disableAccessTaskRef = disableAccessTaskRef;
    }

    public String getEvidenceBundleRef() {
        return evidenceBundleRef;
    }

    public void setEvidenceBundleRef(String evidenceBundleRef) {
        this.evidenceBundleRef = evidenceBundleRef;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
