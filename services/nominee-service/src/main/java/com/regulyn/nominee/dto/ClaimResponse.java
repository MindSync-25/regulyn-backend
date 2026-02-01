package com.regulyn.nominee.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ClaimResponse {

    private UUID claimId;
    private UUID nomineeId;
    private UUID dataPrincipalId;
    private String claimType;
    private String reason;
    private String status;
    private String idempotencyKey;
    private UUID approvedBy;
    private Instant approvedAt;
    private Instant closedAt;
    private String closureNotes;
    private UUID evidenceBundleId;
    private List<DocumentRefResponse> documentRefs;
    private Instant createdAt;
    private Instant updatedAt;

    public static class DocumentRefResponse {
        private UUID docId;
        private String docType;
        private String docRef;
        private String notes;
        private Instant createdAt;

        // Getters and Setters
        public UUID getDocId() {
            return docId;
        }

        public void setDocId(UUID docId) {
            this.docId = docId;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public String getDocRef() {
            return docRef;
        }

        public void setDocRef(String docRef) {
            this.docRef = docRef;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    // Getters and Setters
    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
    }

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public String getClaimType() {
        return claimType;
    }

    public void setClaimType(String claimType) {
        this.claimType = claimType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }

    public UUID getEvidenceBundleId() {
        return evidenceBundleId;
    }

    public void setEvidenceBundleId(UUID evidenceBundleId) {
        this.evidenceBundleId = evidenceBundleId;
    }

    public List<DocumentRefResponse> getDocumentRefs() {
        return documentRefs;
    }

    public void setDocumentRefs(List<DocumentRefResponse> documentRefs) {
        this.documentRefs = documentRefs;
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
