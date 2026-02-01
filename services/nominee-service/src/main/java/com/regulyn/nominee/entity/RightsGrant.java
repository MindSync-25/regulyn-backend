package com.regulyn.nominee.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rights_grants", schema = "nominee")
public class RightsGrant {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;

    @Column(name = "nominee_id", nullable = false)
    private UUID nomineeId;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDataPrincipalId() { return dataPrincipalId; }
    public void setDataPrincipalId(UUID dataPrincipalId) { this.dataPrincipalId = dataPrincipalId; }

    public UUID getNomineeId() { return nomineeId; }
    public void setNomineeId(UUID nomineeId) { this.nomineeId = nomineeId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID claimId) { this.claimId = claimId; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public UUID getRevokedBy() { return revokedBy; }
    public void setRevokedBy(UUID revokedBy) { this.revokedBy = revokedBy; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
