package com.regulyn.nominee.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nominees", schema = "nominee")
public class Nominee {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;

    @Column(name = "nominee_name", nullable = false)
    private String nomineeName;

    @Column(name = "nominee_contact")
    private String nomineeContact;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "relationship")
    private String relationship;

    @Column(name = "scope")
    private String scope;

    @Column(name = "verification_method")
    private String verificationMethod;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDataPrincipalId() { return dataPrincipalId; }
    public void setDataPrincipalId(UUID dataPrincipalId) { this.dataPrincipalId = dataPrincipalId; }

    public String getNomineeName() { return nomineeName; }
    public void setNomineeName(String nomineeName) { this.nomineeName = nomineeName; }

    public String getNomineeContact() { return nomineeContact; }
    public void setNomineeContact(String nomineeContact) { this.nomineeContact = nomineeContact; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public Instant getDisabledAt() { return disabledAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }

    public UUID getDisabledBy() { return disabledBy; }
    public void setDisabledBy(UUID disabledBy) { this.disabledBy = disabledBy; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
