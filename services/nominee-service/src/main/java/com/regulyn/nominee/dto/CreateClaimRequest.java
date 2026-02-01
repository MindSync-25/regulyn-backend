package com.regulyn.nominee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class CreateClaimRequest {

    @NotNull(message = "nomineeId is required")
    private UUID nomineeId;

    @NotNull(message = "dataPrincipalId is required")
    private UUID dataPrincipalId;

    @NotNull(message = "claimType is required")
    private ClaimType claimType;

    @NotBlank(message = "reason is required")
    private String reason;

    @NotEmpty(message = "documentRefs cannot be empty")
    @Valid
    private List<DocumentRef> documentRefs;

    private String idempotencyKey;

    public enum ClaimType {
        RIGHTS_TRANSFER, ACCOUNT_ACCESS, DSAR_SUBMISSION, OTHER
    }

    public static class DocumentRef {
        @NotNull(message = "docType is required")
        private DocType docType;

        @NotBlank(message = "docRef is required")
        private String docRef;

        private String notes;

        public enum DocType {
            DEATH_CERT, POA, ID_PROOF, COURT_ORDER, OTHER
        }

        // Getters and Setters
        public DocType getDocType() {
            return docType;
        }

        public void setDocType(DocType docType) {
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
    }

    // Getters and Setters
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

    public ClaimType getClaimType() {
        return claimType;
    }

    public void setClaimType(ClaimType claimType) {
        this.claimType = claimType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<DocumentRef> getDocumentRefs() {
        return documentRefs;
    }

    public void setDocumentRefs(List<DocumentRef> documentRefs) {
        this.documentRefs = documentRefs;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
